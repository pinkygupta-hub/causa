package com.causa.rca.service;

import com.causa.rca.ai.*;
import com.causa.rca.model.RcaReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
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


    public RcaAnalysisSession startAnalysis(String namespace, String podName) {

        LOG.info("Starting async RCA analysis for: " + namespace + "/" + podName);

        RcaAnalysisSession session = trackingService.createSession(namespace, podName);
        String sessionId = session.sessionId;

        executor.submit(() -> {
            try {
                runAnalysisInternal(sessionId, namespace, podName);
            } catch (Exception e) {
                LOG.error("Error in async analysis for session " + sessionId, e);
            }
        });

        return session;
    }

    @ActivateRequestContext
    void runAnalysisInternal(String sessionId, String namespace, String podName) {

        try {

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

            // -----------------------------
            // Extract Issue + Assertions
            // -----------------------------

            String extractionRaw = issueExtractor.extract(rcaOutput);

            LOG.info("Extraction RAW: " + extractionRaw);

            JsonNode extractionNode = safeParseValidatorOutput(extractionRaw, mapper);

            String issue =
                    extractionNode.has("issue")
                            ? extractionNode.get("issue").asText()
                            : extractionNode.path("issueIdentified").asText("Unknown Issue");

            ArrayNode assertionsNode = (ArrayNode) extractionNode.get("assertions");

            List<Map<String, Object>> finalAssertions = new ArrayList<>();
            List<Future<Map<String, Object>>> futures = new ArrayList<>();

            int supportedCount = 0;

            String logsContext = extractLogs(llmContext);

            // -----------------------------
            // Parallel assertion validation
            // -----------------------------

            for (JsonNode assertionNode : assertionsNode) {

                futures.add(executor.submit(() -> {

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

                    // fallback evidence extraction
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

                    List<String> modelChecks = new ArrayList<>();

                    if (validationNode.has("modelAnalysisQues")) {

                        for (JsonNode q : validationNode.get("modelAnalysisQues")) {
                            modelChecks.add(q.asText());
                        }
                    }

                    String judgement =
                            validationNode.has("judgementCall")
                                    ? validationNode.get("judgementCall").asText()
                                    : "Unsupported";

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

                    if (matchedLogs.isEmpty()) {
                        judgement = "Unsupported";
                        confidence = 0.3;
                    }

                    Map<String, Object> judgmentCall = new LinkedHashMap<>();

                    judgmentCall.put("decision", judgement);
                    judgmentCall.put("confidence", confidence);
                    judgmentCall.put("reasoning", reasoning);

                    Map<String, Object> assertionBlock = new LinkedHashMap<>();

                    assertionBlock.put("assertion", assertion);
                    assertionBlock.put("matchedLogs", matchedLogs);
                    assertionBlock.put("modelAnalysisQuestions", modelChecks);
                    assertionBlock.put("judgmentCall", judgmentCall);

                    return assertionBlock;
                }));
            }

            for (Future<Map<String, Object>> f : futures) {

                Map<String, Object> result = f.get();

                finalAssertions.add(result);

                Map judgment = (Map) result.get("judgmentCall");

                if ("Supported".equals(judgment.get("decision"))) {
                    supportedCount++;
                }
            }

            // -----------------------------
            // Evidence aggregation
            // -----------------------------

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

            // -----------------------------
            // Final decision
            // -----------------------------

            String finalStatus;

            double ratio = (double) supportedCount / finalAssertions.size();

            if (ratio >= 0.7) {
                finalStatus = "Supported";
            } else if (ratio >= 0.3) {
                finalStatus = "Partially Supported";
            } else {
                finalStatus = "Unsupported";
            }

            Map<String, Object> finalDecision = new LinkedHashMap<>();

            finalDecision.put("status", finalStatus);
            finalDecision.put("summary",
                    "Assertions validated independently and aggregated deterministically.");

            Map<String, Object> finalReport = new LinkedHashMap<>();

            finalReport.put("title", "RCA Validation Report");
            finalReport.put("issue", issue);
            finalReport.put("evidence", evidence);
            finalReport.put("supportedLogs", supportedLogs);
            finalReport.put("assertions", finalAssertions);
            finalReport.put("finalDecision", finalDecision);

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

    // -------------------------------------
    // Extract only logs from context
    // -------------------------------------

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

    // -------------------------------------
    // Safe JSON parsing
    // -------------------------------------

    private JsonNode safeParseValidatorOutput(String raw, ObjectMapper mapper) throws Exception {

        if (raw == null || raw.isBlank()) {
            throw new RuntimeException("Model returned empty response");
        }

        // remove markdown
        raw = raw.replace("```json", "")
                .replace("```", "")
                .trim();

        // find JSON start (object starting with a field name)
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

        // fallback
        Map<String,Object> fallback = new HashMap<>();
        fallback.put("matchedLogs", List.of());
        fallback.put("modelAnalysisQues", List.of());
        fallback.put("judgementCall", "Unsupported");
        fallback.put("confidence", 0.3);
        fallback.put("reasoning", raw);

        return mapper.valueToTree(fallback);
    }
}