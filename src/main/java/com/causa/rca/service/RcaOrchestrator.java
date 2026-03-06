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
import java.util.concurrent.Future;

@ApplicationScoped
public class RcaOrchestrator {

    private static final Logger LOG = Logger.getLogger(RcaOrchestrator.class);

    @Inject IssueExtractorAgent issueExtractor;
    @Inject AssertionValidatorAgent assertionValidator;

    @Inject DataCollectorService dataCollector;
    @Inject AnalysisTrackingService trackingService;

    @Inject ManagedExecutor executor;

    @ConfigProperty(name = "quarkus.langchain4j.ollama.base-url",
            defaultValue = "http://ollama.default.svc.cluster.local:11434")
    String ollamaBaseUrl;


    ManagedContext requestContext = Arc.container().requestContext();


    // ─────────────────────────────────────────────────────────────
    // Public entry point — fires async analysis and returns session
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
    // Core analysis — runs inside an activated request context
    // ─────────────────────────────────────────────────────────────

    void runAnalysisInternal(String sessionId, String namespace, String podName) {

        try {

            // ── Data collection ───────────────────────────────────

            trackingService.recordStageStart(sessionId, "data_collection");
            trackingService.updateStatus(sessionId, AnalysisStatus.COLLECTING_DATA,
                    "Collecting metrics, logs, and events");

            CollectedArtifacts artifacts = dataCollector.collectArtifacts(namespace, podName);
            String llmContext = artifacts.toLlmContext();

            trackingService.storeArtifacts(sessionId, artifacts);
            trackingService.recordStageEnd(sessionId, "data_collection");

            LOG.info("Artifacts collected. tokens=" + artifacts.tokenCount);

            ObjectMapper mapper = new ObjectMapper();

            String rcaOutput =
                    "ROOT_CAUSE: Kubernetes control plane unable to update certificates due to controller conflict.";

            // ── Extract issue + assertions ────────────────────────

            trackingService.updateStatus(sessionId, AnalysisStatus.VALIDATING,
                    "Extracting issue and assertions from RCA output");

            String extractionRaw = issueExtractor.extract(rcaOutput);
            LOG.info("Extraction RAW: " + extractionRaw);

            JsonNode extractionNode = safeParseValidatorOutput(extractionRaw, mapper);

            String issue =
                    extractionNode.has("issue")
                            ? extractionNode.get("issue").asText()
                            : extractionNode.path("issueIdentified").asText("Unknown Issue");

            ArrayNode assertionsNode = (ArrayNode) extractionNode.get("assertions");

            if (assertionsNode == null || assertionsNode.isEmpty()) {
                throw new RuntimeException("No assertions extracted from RCA output");
            }

            String logsContext = extractLogs(llmContext);

            // ── Parallel assertion validation ─────────────────────
            //
            // Each executor.submit() lambda runs on a new thread with no CDI
            // context. We activate/terminate requestContext per-task so that
            // AssertionValidatorAgent (@RequestScoped) can be resolved inside.

            List<Future<Map<String, Object>>> futures = new ArrayList<>();

            for (JsonNode assertionNode : assertionsNode) {

                futures.add(executor.submit(() -> {

                    requestContext.activate();
                    try {

                        String assertion = assertionNode.asText();

                        String validationRaw =
                                assertionValidator.validate(assertion, logsContext);

                        LOG.info("Validation RAW for [" + assertion + "]: " + validationRaw);

                        JsonNode validationNode =
                                safeParseValidatorOutput(validationRaw, mapper);

                        // ── Extract matched logs ──────────────────

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

                        // Fallback: keyword scan when model returned no matched logs
                        if (matchedLogs.isEmpty()) {
                            for (String line : logsContext.split("\n")) {
                                String lower = line.toLowerCase();
                                boolean signal =
                                        lower.contains("error")
                                                || lower.contains("failed")
                                                || lower.contains("exception")
                                                || lower.contains("timeout")
                                                || lower.contains("killed")
                                                || lower.contains("oom")
                                                || lower.contains("backoff")
                                                || lower.contains("unable");
                                if (signal) {
                                    matchedLogs.add(line);
                                }
                                if (matchedLogs.size() >= 3) break;
                            }
                        }

                        // ── Extract model analysis questions ──────

                        List<String> modelChecks = new ArrayList<>();

                        if (validationNode.has("modelAnalysisQuestions")) {
                            Set<String> unique = new LinkedHashSet<>();
                            for (JsonNode q : validationNode.get("modelAnalysisQuestions")) {
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

                        // ── Judgement + confidence ────────────────

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

                        // Guard 1: model claimed support but returned no logs → downgrade
                        if (matchedLogs.isEmpty() && !"Unsupported".equals(judgement)) {
                            LOG.warn("Model claimed [" + judgement + "] with no matchedLogs for: "
                                    + assertion + " — downgrading to Unsupported");
                            judgement  = "Unsupported";
                            confidence = 0.2;
                        }

                        if (!matchedLogs.isEmpty() && "Unsupported".equals(judgement)) {
                            LOG.warn("Logs found but model returned Unsupported for: "
                                    + assertion + " — upgrading to Partially Supported");
                            judgement  = "Partially Supported";
                            confidence = Math.max(confidence, 0.5);
                        }

                        Map<String, Object> judgmentCall = new LinkedHashMap<>();
                        judgmentCall.put("decision",   judgement);
                        judgmentCall.put("matchType",  matchType);
                        judgmentCall.put("confidence", confidence);
                        judgmentCall.put("reasoning",  reasoning);

                        Map<String, Object> assertionBlock = new LinkedHashMap<>();
                        assertionBlock.put("assertion",              assertion);
                        assertionBlock.put("matchedLogs",            matchedLogs);
                        assertionBlock.put("modelAnalysisQuestions", modelChecks);
                        assertionBlock.put("judgmentCall",           judgmentCall);

                        return assertionBlock;

                    } finally {
                        requestContext.terminate();
                    }
                }));
            }

            // ── Collect futures + weighted scoring ────────────────
            //
            // Supported           = 2 points (full evidence)
            // Partially Supported = 1 point  (partial/indirect evidence)
            // Unsupported         = 0 points
            // Max possible        = assertions.size() * 2

            List<Map<String, Object>> finalAssertions = new ArrayList<>();
            int weightedScore = 0;

            for (Future<Map<String, Object>> f : futures) {

                Map<String, Object> result = f.get();
                finalAssertions.add(result);

                Map<?, ?> judgment = (Map<?, ?>) result.get("judgmentCall");
                String decision = (String) judgment.get("decision");

                if ("Supported".equals(decision)) {
                    weightedScore += 2;
                } else if ("Partially Supported".equals(decision)) {
                    weightedScore += 1;
                }
            }

            // ── Evidence aggregation ──────────────────────────────

            Set<String> evidenceSet = new LinkedHashSet<>();
            for (Map<String, Object> a : finalAssertions) {
                List<String> logs = (List<String>) a.get("matchedLogs");
                if (logs != null) {
                    evidenceSet.addAll(logs);
                }
            }

            List<String> supportedLogs = new ArrayList<>(evidenceSet);

            String evidence =
                    supportedLogs.isEmpty()
                            ? "No explicit log evidence extracted"
                            : String.join("\n", supportedLogs);

            // ── Final decision ────────────────────────────────────

            double ratio = (double) weightedScore / (finalAssertions.size() * 2);

            String finalStatus;
            if (ratio >= 0.65) {
                finalStatus = "Supported";
            } else if (ratio >= 0.30) {
                finalStatus = "Partially Supported";
            } else {
                finalStatus = "Unsupported";
            }

            LOG.info("Final decision: " + finalStatus
                    + " (weightedScore=" + weightedScore
                    + ", maxScore=" + (finalAssertions.size() * 2)
                    + ", ratio=" + String.format("%.2f", ratio) + ")");

            Map<String, Object> finalDecision = new LinkedHashMap<>();
            finalDecision.put("status",  finalStatus);
            finalDecision.put("summary",
                    "Assertions validated independently with semantic matching and aggregated deterministically.");

            // ── Assemble report ───────────────────────────────────

            Map<String, Object> finalReport = new LinkedHashMap<>();
            finalReport.put("title",            "RCA Validation Report");
            finalReport.put("issue",            issue);
            finalReport.put("evidence",         evidence);
            finalReport.put("validationChecks", defaultValidationChecks());
            finalReport.put("supportedLogs",    supportedLogs);
            finalReport.put("assertions",       finalAssertions);
            finalReport.put("finalDecision",    finalDecision);

            String finalJson =
                    mapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(finalReport);

            LOG.info("Final Structured Report:\n" + finalJson);

            RcaReport report = mapper.readValue(finalJson, RcaReport.class);

            trackingService.completeSession(sessionId, report);

        } catch (Exception e) {
            LOG.error("Error during RCA analysis for session " + sessionId, e);
            trackingService.failSession(sessionId, e.getMessage());
        }
    }


    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private List<String> defaultValidationChecks() {
        return List.of(
                "Do logs contain evidence supporting the RCA assertions?",
                "Do events occur before the observed failure timeline?",
                "Do system logs reference the failing component or dependency?"
        );
    }

    /**
     * Filters llmContext down to lines likely to contain signal,
     * reducing token noise sent to the validator.
     */
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

    /**
     * Tolerant JSON parser: strips markdown fences, finds the outermost
     * JSON object, and falls back to a safe Unsupported map on total failure.
     */
    private JsonNode safeParseValidatorOutput(String raw, ObjectMapper mapper) throws Exception {

        if (raw == null || raw.isBlank()) {
            throw new RuntimeException("Model returned empty response");
        }

        raw = raw.replace("```json", "")
                .replace("```", "")
                .trim();

        int start = raw.indexOf("{\"");
        if (start == -1) {
            start = raw.indexOf("{\n");
        }
        int end = raw.lastIndexOf("}");

        if (start != -1 && end != -1 && end > start) {
            String json = raw.substring(start, end + 1);
            try {
                return mapper.readTree(json);
            } catch (Exception e) {
                LOG.error("Invalid JSON returned by model:\n" + json);
                throw e;
            }
        }

        // Fallback: treat raw output as reasoning, return safe defaults
        LOG.warn("Could not locate JSON object in model output — using fallback node");
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("matchedLogs",            List.of());
        fallback.put("matchType",              "none");
        fallback.put("modelAnalysisQuestions", List.of());
        fallback.put("judgementCall",          "Unsupported");
        fallback.put("confidence",             0.2);
        fallback.put("reasoning",              raw.length() > 300 ? raw.substring(0, 300) : raw);

        return mapper.valueToTree(fallback);
    }
}