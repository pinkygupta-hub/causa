package com.causa.rca.service;

import com.causa.rca.ai.*;
import com.causa.rca.model.RcaReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;

import com.causa.rca.model.RcaAnalysisSession;
import com.causa.rca.model.AnalysisStatus;
import com.causa.rca.model.artifact.CollectedArtifacts;

import java.util.*;


/**
 * RCA Orchestrator
 *
 * Coordinates the complete RCA pipeline.
 */
@ApplicationScoped
public class RcaOrchestrator {

    private static final Logger LOG = Logger.getLogger(RcaOrchestrator.class);

    @Inject
    DataCollectorService dataCollector;

    @Inject
    AnomalyDetector anomalyDetector;

    @Inject RcaAssertionExtractor rcaAssertionExtractor;
    @Inject AssertionValidatorAgent assertionValidator;

    @Inject
    RootCauseAnalyst rootCauseAnalyst;

    @Inject
    AnalysisTrackingService trackingService;

    @ConfigProperty(name = "quarkus.langchain4j.ollama.detector.chat-model.model-id", defaultValue = "llama2:7b-chat-q8_0")
    String detectorModel;

    @ConfigProperty(name = "quarkus.langchain4j.ollama.rca.chat-model.model-id", defaultValue = "llama2:7b-chat-q8_0")
    String rcaModel;

    @ConfigProperty(name = "quarkus.langchain4j.ollama.base-url",
            defaultValue = "http://ollama.default.svc.cluster.local:11434")
    String ollamaBaseUrl;

    // ─────────────────────────────────────────────────────────────
    // Async Execution
    // ─────────────────────────────────────────────────────────────

    @Inject ManagedExecutor executor;

    ManagedContext requestContext = Arc.container().requestContext();

    // ─────────────────────────────────────────────────────────────
    // Async Entry Point
    // ─────────────────────────────────────────────────────────────

    public RcaAnalysisSession startAnalysis(String namespace, String podName) {

        LOG.info("Starting async RCA analysis for: " + namespace + "/" + podName);

        RcaAnalysisSession session = trackingService.createSession(namespace, podName);
        String sessionId = session.sessionId;

        executor.runAsync(() -> {
            requestContext.activate();
            try {
                runAnalysisInternal(sessionId, namespace, podName);
            } catch (Exception e) {
                LOG.error("Error in async analysis for session " + sessionId, e);
            } finally {
                requestContext.terminate();
            }
        });

        return session;
    }


    // ─────────────────────────────────────────────────────────────
    // RCA Pipeline
    // ─────────────────────────────────────────────────────────────

    void runAnalysisInternal(String sessionId, String namespace, String podName) {

        try {

            // ── Step 0: Data Collection ───────────────────────────────────────
            trackingService.recordStageStart(sessionId, "data_collection");
            trackingService.updateStatus(sessionId, AnalysisStatus.COLLECTING_DATA,
                    "Collecting metrics, logs, and events from Kubernetes");

            CollectedArtifacts artifacts = dataCollector.collectArtifacts(namespace, podName);

            // The compact LLM context – summaries only, never raw data
            String llmContext = artifacts.toLlmContext();

            LOG.info("Data collection complete for " + podName
                    + " | tokens=" + artifacts.tokenCount
                    + " | truncated=" + artifacts.truncationApplied
                    + " | llmContextLength=" + llmContext.length());
            LOG.debug("LLM context:\n" + llmContext);

            // Persist full artifacts to MongoDB so UX can display raw evidence
            trackingService.storeArtifacts(sessionId, artifacts);

            trackingService.recordStageEnd(sessionId, "data_collection");

            LOG.info("Artifacts collected. tokens=" + artifacts.tokenCount);

            ObjectMapper mapper = new ObjectMapper();
            // ── Step 1: Anomaly Detection ─────────────────────────────────────
            trackingService.recordStageStart(sessionId, "anomaly_detection");
            trackingService.updateStatus(sessionId, AnalysisStatus.DETECTING_ANOMALY,
                    "Analyzing data to detect anomalies using AI");

            LOG.info("Step 1: Anomaly Detection with model: " + detectorModel);

            String rawAnomaly;
            try {
                // LLM receives ONLY the compact summary context
                rawAnomaly = anomalyDetector.detectAnomaly(llmContext);
                LOG.info("RAW Anomaly Detector Response: [" + rawAnomaly + "]");
            } catch (Exception e) {
                String errorMsg = buildModelErrorMessage("anomaly detection", detectorModel, e);
                LOG.error(errorMsg, e);
                throw new RuntimeException(errorMsg, e);
            }

            // Parse anomaly type token from raw response.
            // Handles both clean single-token responses ("OOM_KILLED") and
            // verbose responses like "ANAMOLY_TYPE: OTHERS" or "ANOMALY_TYPE: HEALTHY"
            String anomalyType = parseAnomalyType(rawAnomaly);
            LOG.info("Sanitized Anomaly Type: [" + anomalyType + "]");
            trackingService.recordStageEnd(sessionId, "anomaly_detection");

            if (anomalyType.isEmpty()
                    || "HEALTHY".equalsIgnoreCase(anomalyType)
                    || anomalyType.toUpperCase().contains("HEALTHY")) {
                LOG.info("System is healthy – skipping RCA and Validation.");
                RcaReport healthyReport = new RcaReport(
                        "System Healthy", "No anomaly detected",
                        "Metrics within normal range", null,null,null,null);
                trackingService.markHealthy(sessionId, healthyReport);
                return;
            }

            // ── Step 2: Root Cause Analysis ───────────────────────────────────
            trackingService.recordStageStart(sessionId, "rca_analysis");
            trackingService.updateStatus(sessionId, AnalysisStatus.ANALYZING_RCA,
                    "Performing root cause analysis for: " + anomalyType);

            LOG.info("Step 2: Root Cause Analysis with model: " + rcaModel);

            String rcaOutput;
            try {
                // LLM receives ONLY the compact summary context
                rcaOutput = rootCauseAnalyst.analyzeRootCause(anomalyType, llmContext);
                LOG.info("RAW RCA Result: [" + rcaOutput + "]");
            } catch (Exception e) {
                String errorMsg = buildModelErrorMessage("root cause analysis", rcaModel, e);
                LOG.error(errorMsg, e);
                throw new RuntimeException(errorMsg, e);
            }
            trackingService.recordStageEnd(sessionId, "rca_analysis");

            // ── Step 3: Validation & Formatting ──────────────────────────────
            trackingService.recordStageStart(sessionId, "validation");


            // ─────────────────────────────────────────────────────
            // Step 2 — Extract Issue + Assertions
            // ─────────────────────────────────────────────────────

            trackingService.updateStatus(
                    sessionId,
                    AnalysisStatus.VALIDATING,
                    "Extracting issue and assertions from RCA output"
            );

            String extractionRaw = rcaAssertionExtractor.extract(rcaOutput);
            LOG.info("Extraction RAW: " + extractionRaw);

            JsonNode extractionNode =
                    safeParseValidatorOutput(extractionRaw, mapper);

            String issue =
                    extractionNode.has("issue")
                            ? extractionNode.get("issue").asText()
                            : extractionNode.path("issueIdentified")
                            .asText("Unknown Issue");

            ArrayNode assertionsNode =
                    (ArrayNode) extractionNode.get("assertions");

            if (assertionsNode == null || assertionsNode.isEmpty()) {
                throw new RuntimeException(
                        "No assertions extracted from RCA output");
            }


            String logsContext = extractLogs(llmContext);


            // ─────────────────────────────────────────────────────
            // Step 3 — Assertion Validation
            // ─────────────────────────────────────────────────────

            List<Map<String, Object>> finalAssertions = new ArrayList<>();
            int weightedScore = 0;

            for (JsonNode assertionNode : assertionsNode) {

                String assertion = assertionNode.asText();

                String validationRaw =
                        assertionValidator.validate(assertion, logsContext);

                LOG.info("Validation RAW for [" + assertion + "]: " + validationRaw);

                JsonNode validationNode =
                        safeParseValidatorOutput(validationRaw, mapper);


                List<String> matchedLogs = new ArrayList<>();

                if (validationNode.has("matchedLogs")) {

                    for (JsonNode log : validationNode.get("matchedLogs")) {

                        String l = log.asText();

                        if (l.length() < 400) {
                            matchedLogs.add(l);
                        }

                        if (matchedLogs.size() >= 3) break;
                    }
                }


                if (matchedLogs.isEmpty()) {

                    for (String line : logsContext.split("\n")) {

                        String lower = line.toLowerCase();

                        boolean signal =
                                line.toLowerCase().contains("error")
                                        || line.toLowerCase().contains("failed")
                                        || line.toLowerCase().contains("exception")
                                        || line.toLowerCase().contains("timeout")
                                        || line.toLowerCase().contains("killed")
                                        || line.toLowerCase().contains("oom")
                                        || line.toLowerCase().contains("backoff")
                                        || line.toLowerCase().contains("unable")
                                        || line.toLowerCase().contains("conflict")
                                        || line.toLowerCase().contains("certificate")
                                        || line.toLowerCase().contains("network");

                        if (signal) {
                            matchedLogs.add(line);
                        }

                        if (matchedLogs.size() >= 3) break;
                    }
                }


                List<String> modelChecks = new ArrayList<>();

                if (validationNode.has("modelAnalysisQuestions")) {

                    Set<String> unique = new LinkedHashSet<>();

                    for (JsonNode q :
                            validationNode.get("modelAnalysisQuestions")) {

                        String question = q.asText().trim();

                        if (!question.isEmpty()) {
                            unique.add(question);
                        }

                        if (unique.size() >= 3) break;
                    }

                    modelChecks = new ArrayList<>(unique);
                }

                if (modelChecks.isEmpty()) {

                    modelChecks = List.of(
                            "Do logs contain evidence supporting this assertion?",
                            "Do events occur before the observed failure timeline?",
                            "Do logs reference the component related to this assertion?"
                    );
                }


                String judgement =
                        validationNode.has("judgementCall")
                                ? validationNode.get("judgementCall").asText()
                                : "Unsupported";

                String matchType =
                        validationNode.has("matchType")
                                ? validationNode.get("matchType").asText()
                                : "none";

                double confidence =
                        validationNode.has("confidence")
                                ? validationNode.get("confidence").asDouble()
                                : 0.3;

                String reasoning =
                        validationNode.has("reasoning")
                                ? validationNode.get("reasoning").asText()
                                : "Validator returned non-JSON output.";

                if (reasoning.length() > 400) {
                    reasoning = reasoning.substring(0, 400);
                }


                if (matchedLogs.isEmpty()
                        && !"Unsupported".equals(judgement)) {

                    LOG.warn(
                            "Model claimed [" + judgement
                                    + "] but returned no logs. Downgrading."
                    );

                    judgement = "Unsupported";
                    confidence = 0.2;
                }


                Map<String, Object> judgmentCall =
                        new LinkedHashMap<>();

                judgmentCall.put("decision", judgement);
                judgmentCall.put("matchType", matchType);
                judgmentCall.put("confidence", confidence);
                judgmentCall.put("reasoning", reasoning);


                Map<String, Object> assertionBlock =
                        new LinkedHashMap<>();

                assertionBlock.put("assertion", assertion);
                assertionBlock.put("matchedLogs", matchedLogs);
                assertionBlock.put("modelAnalysisQuestions", modelChecks);
                assertionBlock.put("judgmentCall", judgmentCall);

                finalAssertions.add(assertionBlock);


                if ("Supported".equals(judgement)) {
                    weightedScore += 2;
                } else if ("Partially Supported".equals(judgement)) {
                    weightedScore += 1;
                }
            }


            // ─────────────────────────────────────────────────────
            // Evidence Aggregation
            // ─────────────────────────────────────────────────────

            Set<String> evidenceSet = new LinkedHashSet<>();

            for (Map<String, Object> a : finalAssertions) {

                List<String> logs =
                        (List<String>) a.get("matchedLogs");

                if (logs != null) {
                    evidenceSet.addAll(logs);
                }
            }

            List<String> supportedLogs =
                    new ArrayList<>(evidenceSet);

            String evidence =
                    supportedLogs.isEmpty()
                            ? "No explicit log evidence extracted"
                            : String.join("\n", supportedLogs);


            double ratio =
                    (double) weightedScore /
                            (finalAssertions.size() * 2);

            String finalStatus;

            if (ratio >= 0.65) {
                finalStatus = "Supported";
            } else if (ratio >= 0.30) {
                finalStatus = "Partially Supported";
            } else {
                finalStatus = "Unsupported";
            }


            Map<String, Object> finalDecision =
                    new LinkedHashMap<>();

            finalDecision.put("status", finalStatus);
            finalDecision.put(
                    "summary",
                    "Assertions validated independently and aggregated deterministically."
            );


            Map<String, Object> finalReport =
                    new LinkedHashMap<>();

            finalReport.put("title", "RCA Validation Report");
            finalReport.put("issue", issue);
            finalReport.put("evidence", evidence);
            finalReport.put("validationChecks", defaultValidationChecks());
            finalReport.put("supportedLogs", supportedLogs);
            finalReport.put("assertions", finalAssertions);
            finalReport.put("finalDecision", finalDecision);


            String finalJson =
                    mapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(finalReport);

            LOG.info("Final Structured Report pinky 1:\n" + finalJson);


            RcaReport report =
                    mapper.readValue(finalJson, RcaReport.class);

            trackingService.completeSession(sessionId, report);

        } catch (Exception e) {

            LOG.error(
                    "Error during RCA analysis for session "
                            + sessionId,
                    e
            );

            trackingService.failSession(sessionId, e.getMessage());
        }
    }


    private List<String> defaultValidationChecks() {

        return List.of(
                "Do logs contain evidence supporting the RCA assertions?",
                "Do events occur before the observed failure timeline?",
                "Do system logs reference the failing component or dependency?"
        );
    }


    private String extractLogs(String context) {

        StringBuilder logs = new StringBuilder();

        for (String line : context.split("\n")) {

            if (line.contains("error")
                    || line.contains("failed")
                    || line.contains("Exception")
                    || line.contains("E0")
                    || line.contains("I0")
                    || line.contains("Back-off")) {

                logs.append(line).append("\n");
            }
        }

        return logs.toString();
    }


    private JsonNode safeParseValidatorOutput(
            String raw,
            ObjectMapper mapper
    ) throws Exception {

        if (raw == null || raw.isBlank()) {
            throw new RuntimeException("Model returned empty response");
        }

        raw = raw.replace("```json", "")
                .replace("```", "")
                .trim();

        int start = raw.indexOf("{");
        int end = raw.lastIndexOf("}");

        if (start != -1 && end != -1 && end > start) {

            String json = raw.substring(start, end + 1);

            // ─────────────────────────────────
            // LLM JSON Repair (critical)
            // ─────────────────────────────────

            json = json
                    .replaceAll(",\\s*]", "]")   // remove trailing commas in arrays
                    .replaceAll(",\\s*}", "}")   // remove trailing commas in objects
                    .replaceAll("\\\\n", " ")    // remove escaped newlines
                    .trim();

            try {
                return mapper.readTree(json);
            } catch (Exception e) {

                LOG.error("Invalid JSON returned by model after cleanup:\n" + json);

                throw e;
            }
        }

        LOG.warn("No JSON found in model output. Using fallback.");

        Map<String, Object> fallback = new HashMap<>();

        fallback.put("matchedLogs", List.of());
        fallback.put("matchType", "none");
        fallback.put("modelAnalysisQuestions", List.of());
        fallback.put("judgementCall", "Unsupported");
        fallback.put("confidence", 0.2);
        fallback.put("reasoning", raw);

        return mapper.valueToTree(fallback);
    }
    /**
     * Parses the anomaly type token from the raw LLM response.
     * <p>
     * Handles both clean single-token responses (e.g. {@code "OOM_KILLED"}) and
     * verbose responses where the model prefixes the token with a label, e.g.:
     * <ul>
     *   <li>{@code "ANAMOLY_TYPE: OTHERS"}</li>
     *   <li>{@code "ANOMALY_TYPE: HEALTHY"}</li>
     *   <li>{@code "ANOMALY: CPU_THROTTLING"}</li>
     * </ul>
     * The valid token set is: OOM_KILLED, GC_PAUSE, CPU_THROTTLING, CRASH_LOOP,
     * IMAGE_PULL_BACKOFF, HEALTHY, OTHERS.
     *
     * @param raw the raw string returned by the anomaly detector LLM
     * @return the extracted anomaly type token in uppercase, or {@code "OTHERS"} if unparseable
     */
    private String parseAnomalyType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "OTHERS";
        }

        // Valid tokens (including common misspelling "ANAMOLY")
        java.util.Set<String> validTokens = java.util.Set.of(
                "OOM_KILLED", "GC_PAUSE", "CPU_THROTTLING", "CRASH_LOOP",
                "IMAGE_PULL_BACKOFF", "HEALTHY", "OTHERS");

        // 1. Check if the response contains "KEY: VALUE" pattern (e.g. "ANAMOLY_TYPE: OTHERS")
        //    Search all lines for a colon-separated value that matches a valid token
        for (String line : raw.split("\n")) {
            if (line.contains(":")) {
                String value = line.substring(line.indexOf(':') + 1).split("#")[0].trim().toUpperCase();
                if (validTokens.contains(value)) {
                    return value;
                }
            }
        }

        // 2. Scan each line for a standalone valid token
        for (String line : raw.split("\n")) {
            String candidate = line.split("#")[0].trim().toUpperCase();
            if (validTokens.contains(candidate)) {
                return candidate;
            }
        }

        // 3. Scan word-by-word across the entire response
        for (String word : raw.toUpperCase().split("[\\s,.:;\\[\\]()]+")) {
            if (validTokens.contains(word)) {
                return word;
            }
        }

        // 4. Fallback: return first non-empty line token, uppercased
        String firstLine = raw.split("\n")[0].split("#")[0].trim().toUpperCase();
        return firstLine.isEmpty() ? "OTHERS" : firstLine;
    }

    private String buildModelErrorMessage(String stage, String model, Exception cause) {
        return String.format(
                "Failed to perform %s using model '%s' at %s. "
                        + "Ensure the model is available: "
                        + "kubectl exec -it <ollama-pod> -- ollama pull %s. "
                        + "Cause: %s",
                stage, model, ollamaBaseUrl, model, cause.getMessage());
    }
}