package com.causa.rca.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.causa.rca.ai.AnomalyDetector;
import com.causa.rca.ai.RootCauseAnalyst;
import com.causa.rca.ai.ValidationAgent;
import com.causa.rca.model.RcaReport;
import com.causa.rca.model.RcaAnalysisSession;
import com.causa.rca.model.AnalysisStatus;
import com.causa.rca.model.artifact.CollectedArtifacts;

/**
 * Orchestrator service that coordinates the complete RCA (Root Cause Analysis) pipeline.
 *
 * <h3>Pipeline steps</h3>
 * <ol>
 *   <li><b>Data Collection</b> – {@link DataCollectorService#collectArtifacts} gathers all
 *       diagnostic data and packages it into a {@link CollectedArtifacts} object.</li>
 *   <li><b>Anomaly Detection</b> – {@link AnomalyDetector} receives only the LLM-safe
 *       context string ({@link CollectedArtifacts#toLlmContext()}).</li>
 *   <li><b>Root Cause Analysis</b> – {@link RootCauseAnalyst} receives the same compact
 *       context plus the detected anomaly type.</li>
 *   <li><b>Validation & Formatting</b> – {@link ValidationAgent} receives the RCA output
 *       and the compact context to produce a structured {@link RcaReport}.</li>
 * </ol>
 *
 * <h3>LLM data contract</h3>
 * LLM services receive <b>only</b> {@link CollectedArtifacts#toLlmContext()} — a compact
 * string built from summaries.  Raw logs, raw events, and full JFR reports are
 * <b>never</b> passed to LLMs.
 *
 * @see DataCollectorService
 * @see AnomalyDetector
 * @see RootCauseAnalyst
 * @see ValidationAgent
 * @see CollectedArtifacts
 */
@ApplicationScoped
public class RcaOrchestrator {

    private static final Logger LOG = Logger.getLogger(RcaOrchestrator.class);

    @Inject
    DataCollectorService dataCollector;

    @Inject
    AnomalyDetector anomalyDetector;

    @Inject
    RootCauseAnalyst rootCauseAnalyst;

    @Inject
    ValidationAgent reportValidator;

    @Inject
    AnalysisTrackingService trackingService;

    @ConfigProperty(name = "quarkus.langchain4j.ollama.detector.chat-model.model-id", defaultValue = "phi3:mini")
    String detectorModel;

    @ConfigProperty(name = "quarkus.langchain4j.ollama.rca.chat-model.model-id", defaultValue = "phi3:mini")
    String rcaModel;

    @ConfigProperty(name = "quarkus.langchain4j.ollama.validator.chat-model.model-id", defaultValue = "phi3:mini")
    String validatorModel;

    @ConfigProperty(name = "quarkus.langchain4j.ollama.base-url",
            defaultValue = "http://ollama.default.svc.cluster.local:11434")
    String ollamaBaseUrl;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Starts an asynchronous RCA analysis pipeline for a specific pod.
     * Returns immediately with the created session; analysis runs in a background thread.
     *
     * @param namespace the Kubernetes namespace
     * @param podName   the pod name
     * @return the created {@link RcaAnalysisSession} with status {@code IN_PROGRESS}
     */
    public RcaAnalysisSession startAnalysis(String namespace, String podName) {
        LOG.info("Starting async RCA analysis for: " + namespace + "/" + podName);

        RcaAnalysisSession session = trackingService.createSession(namespace, podName);
        String sessionId = session.sessionId;

        // Run analysis asynchronously in a background thread
        new Thread(() -> {
            try {
                runAnalysisInternal(sessionId, namespace, podName);
            } catch (Exception e) {
                LOG.error("Error in async analysis for session " + sessionId, e);
            }
        }).start();

        return session;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal pipeline
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Executes the full RCA pipeline synchronously.
     * {@code @ActivateRequestContext} ensures RequestScoped beans work in the async thread.
     */
    @ActivateRequestContext
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

            // Sanitize: take first line, strip comments
            String anomalyType = rawAnomaly.split("\n")[0].split("#")[0].trim();
            LOG.info("Sanitized Anomaly Type: [" + anomalyType + "]");
            trackingService.recordStageEnd(sessionId, "anomaly_detection");

            if (anomalyType.isEmpty()
                    || "HEALTHY".equalsIgnoreCase(anomalyType)
                    || anomalyType.toUpperCase().contains("HEALTHY")) {
                LOG.info("System is healthy – skipping RCA and Validation.");
                RcaReport healthyReport = new RcaReport(
                        "System Healthy", "No anomaly detected",
                        "Metrics within normal range", null,
                        1.0);
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
            trackingService.updateStatus(sessionId, AnalysisStatus.VALIDATING,
                    "Validating and formatting analysis results");

            LOG.info("Step 3: Validation with model: " + validatorModel);

            RcaReport report;
            try {
                // LLM receives ONLY the compact summary context (not raw logs/events)
                report = reportValidator.validateAndFormat(rcaOutput, llmContext);
                LOG.info("Final Report: " + report);
            } catch (Exception e) {
                String errorMsg = buildModelErrorMessage("validation", validatorModel, e);
                LOG.error(errorMsg, e);
                throw new RuntimeException(errorMsg, e);
            }
            trackingService.recordStageEnd(sessionId, "validation");

            trackingService.completeSession(sessionId, report);

        } catch (Exception e) {
            LOG.error("Error during RCA analysis for session " + sessionId, e);
            trackingService.failSession(sessionId, e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String buildModelErrorMessage(String stage, String model, Exception cause) {
        return String.format(
                "Failed to perform %s using model '%s' at %s. "
                + "Ensure the model is available: "
                + "kubectl exec -it <ollama-pod> -- ollama pull %s. "
                + "Cause: %s",
                stage, model, ollamaBaseUrl, model, cause.getMessage());
    }
}

// Made with Bob
