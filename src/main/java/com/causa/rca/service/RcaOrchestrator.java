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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class RcaOrchestrator {

    private static final Logger LOG = Logger.getLogger(RcaOrchestrator.class);

    @Inject DataCollectorService dataCollector;
    @Inject AnomalyDetector anomalyDetector;
    @Inject RcaAssertionExtractor rcaAssertionExtractor;
    @Inject EvidenceMatcherAgent evidenceMatcher;
    @Inject AssertionValidatorAgent assertionValidator;
    @Inject RootCauseAnalyst rootCauseAnalyst;
    @Inject AnalysisTrackingService trackingService;

    @ConfigProperty(name = "quarkus.langchain4j.ollama.detector.chat-model.model-id", defaultValue = "llama2:7b-chat-q8_0")
    String detectorModel;

    @ConfigProperty(name = "quarkus.langchain4j.ollama.rca.chat-model.model-id", defaultValue = "llama2:7b-chat-q8_0")
    String rcaModel;

    @ConfigProperty(name = "quarkus.langchain4j.ollama.base-url",
            defaultValue = "http://ollama.default.svc.cluster.local:11434")
    String ollamaBaseUrl;

    @Inject ManagedExecutor executor;
    ManagedContext requestContext = Arc.container().requestContext();

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

    void runAnalysisInternal(String sessionId, String namespace, String podName) {

        try {

            trackingService.recordStageStart(sessionId, "data_collection");
            trackingService.updateStatus(sessionId, AnalysisStatus.COLLECTING_DATA,
                    "Collecting metrics, logs, and events from Kubernetes");

            CollectedArtifacts artifacts = dataCollector.collectArtifacts(namespace, podName);
            String llmContext = artifacts.toLlmContext();

            trackingService.storeArtifacts(sessionId, artifacts);
            trackingService.recordStageEnd(sessionId, "data_collection");

            ObjectMapper mapper = new ObjectMapper();

            trackingService.recordStageStart(sessionId, "anomaly_detection");
            trackingService.updateStatus(sessionId, AnalysisStatus.DETECTING_ANOMALY,
                    "Analyzing data to detect anomalies using AI");

            String rawAnomaly = anomalyDetector.detectAnomaly(llmContext);
            String anomalyType = parseAnomalyType(rawAnomaly);

            trackingService.recordStageEnd(sessionId, "anomaly_detection");

            if ("HEALTHY".equalsIgnoreCase(anomalyType)) {

                RcaReport healthyReport = new RcaReport(
                        "System Healthy",
                        "No anomaly detected",
                        "Metrics within normal range",
                        null,null,null,null);

                trackingService.markHealthy(sessionId, healthyReport);
                return;
            }

            trackingService.recordStageStart(sessionId, "rca_analysis");

            String rcaOutput =
                    rootCauseAnalyst.analyzeRootCause(anomalyType, llmContext);
            LOG.info("rca output from  rootCauseAnalyst " +  rcaOutput);

            trackingService.recordStageEnd(sessionId, "rca_analysis");

            trackingService.recordStageStart(sessionId, "validation");

            String rcaSection = extractRootCause(rcaOutput);
            LOG.info("rca section sent as input to assertion extractor " +  rcaSection);
            String extractionRaw = rcaAssertionExtractor.extract(rcaSection);
            LOG.info("assertions output " +extractionRaw);

            JsonNode extractionNode =
                    safeParseValidatorOutput(extractionRaw, mapper);

            String issue =
                    extractionNode.path("issueIdentified")
                            .asText("Unknown Issue");

            ArrayNode assertionsNode =
                    (ArrayNode) extractionNode.get("assertions");

            String logsContext = llmContext;

            List<Map<String, Object>> finalAssertions = new ArrayList<>();
            int weightedScore = 0;

            for (JsonNode assertionNode : assertionsNode) {

                String assertion = assertionNode.asText();

                LOG.info("evidence i/p for assertion " + assertion + " - logs - " + logsContext);


                String evidenceRaw =
                        evidenceMatcher.match(assertion, logsContext);

                LOG.info(" evidenceRaw o/p " + evidenceRaw + " for assertion " + assertion + "logs " + logsContext);

                JsonNode evidenceNode =
                        safeParseValidatorOutput(evidenceRaw, mapper);

                List<String> matchedLogs = new ArrayList<>();

                if (evidenceNode.has("matchedLogs")) {

                    for (JsonNode log : evidenceNode.get("matchedLogs")) {

                        String l;

                        if (log.isTextual()) {
                            l = log.asText();
                        } else if (log.has("text")) {
                            l = log.get("text").asText();
                        } else {
                            continue;
                        }

                        if (l.length() < 400) {
                            matchedLogs.add(l);
                        }

                        if (matchedLogs.size() >= 3) break;
                    }
                }
                LOG.info("sending to validator assertion" + assertion + " matched Logs: " + matchedLogs);
                String validationRaw =
                        assertionValidator.validate(
                                assertion,
                                String.join("\n", matchedLogs));


                LOG.info("validation o/p from validator : " + validationRaw);
                JsonNode validationNode =
                        safeParseValidatorOutput(validationRaw, mapper);

                List<String> modelChecks = new ArrayList<>();

                if (validationNode.has("modelAnalysisQuestions")) {

                    for (JsonNode q :
                            validationNode.get("modelAnalysisQuestions")) {

                        modelChecks.add(q.asText());

                        if (modelChecks.size() >= 3) break;
                    }
                }

                String judgement =
                        validationNode.path("judgementCall")
                                .asText("Unsupported");

                String matchType =
                        validationNode.path("matchType")
                                .asText("none");

                double confidence =
                        validationNode.path("confidence")
                                .asDouble(0.3);

                String reasoning =
                        validationNode.path("reasoning")
                                .asText("");

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

            Set<String> evidenceSet = new LinkedHashSet<>();

            for (Map<String, Object> a : finalAssertions) {
                evidenceSet.addAll((List<String>) a.get("matchedLogs"));
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

            String finalStatus =
                    ratio >= 0.65 ? "Supported"
                            : ratio >= 0.30 ? "Partially Supported"
                            : "Unsupported";

            Map<String, Object> finalDecision =
                    Map.of(
                            "status", finalStatus,
                            "summary",
                            "Assertions validated independently and aggregated deterministically."
                    );

            Map<String, Object> finalReport =
                    new LinkedHashMap<>();

            finalReport.put("title", "RCA Validation Report");
            finalReport.put("issue", issue);
            finalReport.put("evidence", evidence);
            finalReport.put("supportedLogs", supportedLogs);
            finalReport.put("assertions", finalAssertions);
            finalReport.put("finalDecision", finalDecision);

            String finalJson =
                    mapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(finalReport);

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

    private String extractLogs(String context) {

        StringBuilder logs = new StringBuilder();

        for (String line : context.split("\n")) {

            String lower = line.toLowerCase();

            if (lower.contains("oom")
                    || lower.contains("killed")
                    || lower.contains("crashloop")
                    || lower.contains("backoff")
                    || lower.contains("error")
                    || lower.contains("failed")) {

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

            Map<String,Object> fallback = new HashMap<>();
            fallback.put("matchedLogs", List.of());
            fallback.put("matchType","none");
            fallback.put("modelAnalysisQuestions", List.of());
            fallback.put("judgementCall","Unsupported");
            fallback.put("confidence",0.2);
            fallback.put("reasoning","Model returned empty response");

            return mapper.valueToTree(fallback);
        }

        raw = raw.replace("```json","")
                .replace("```","")
                .trim();

        int start = raw.indexOf("{");
        int end = raw.lastIndexOf("}");

        // ✅ If JSON exists
        if(start != -1 && end != -1 && end > start){

            String json = raw.substring(start, end + 1);

            json = json
                    .replaceAll(",\\s*]","]")
                    .replaceAll(",\\s*}","}")
                    .replaceAll("\\\\n"," ")
                    .trim();

            try {
                return mapper.readTree(json);
            }
            catch(Exception e){
                LOG.warn("Invalid JSON from model, falling back:\n"+raw);
            }
        }

        // ✅ Fallback when model returned text
        LOG.warn("Model returned non-JSON output:\n" + raw);

        Map<String,Object> fallback = new HashMap<>();
        fallback.put("matchedLogs", List.of());
        fallback.put("matchType","none");
        fallback.put("modelAnalysisQuestions", List.of());
        fallback.put("judgementCall","Unsupported");
        fallback.put("confidence",0.2);
        fallback.put("reasoning", raw);

        return mapper.valueToTree(fallback);
    }

    private String parseAnomalyType(String raw) {

        if (raw == null) return "OTHERS";

        for (String word : raw.toUpperCase().split("[\\s:]+")) {

            if (word.equals("OOM_KILLED")
                    || word.equals("GC_PAUSE")
                    || word.equals("CPU_THROTTLING")
                    || word.equals("CRASH_LOOP")
                    || word.equals("IMAGE_PULL_BACKOFF")
                    || word.equals("HEALTHY")) {

                return word;
            }
        }

        return "OTHERS";
    }

    private String extractRootCause(String rcaOutput) {

        Pattern pattern = Pattern.compile(
                "(?i)ROOT[_ ]CAUSE\\s*:\\s*(.*?)(KEY[_ ]EVIDENCE|SUPPORTED LOGS|RECOMMENDATIONS|$)",
                Pattern.DOTALL);

        Matcher matcher = pattern.matcher(rcaOutput);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return rcaOutput.trim();
    }
}