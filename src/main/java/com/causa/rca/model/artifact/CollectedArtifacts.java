package com.causa.rca.model.artifact;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Top-level container for all diagnostic artifacts collected for a pod.
 * <p>
 * This replaces the previous {@code Map<String, String>} returned by
 * {@code DataCollectorService.getRealDataPackage()}.  Each artifact separates
 * raw data (for UX display) from LLM-safe summaries (for AI services).
 * </p>
 *
 * <h3>Data-flow contract</h3>
 * <pre>
 *   DataCollectorService  →  CollectedArtifacts
 *                                 │
 *                    ┌────────────┴────────────┐
 *                    ▼                         ▼
 *              LLM services              UX / REST API
 *         (summaries only)           (raw + dedup + summary)
 * </pre>
 *
 * <h3>Token budget</h3>
 * After collection, {@link com.causa.rca.utils.TokenBudgetEnforcer} enforces
 * a hard cap of 4 000 tokens on the combined LLM context.  The fields
 * {@link #tokenCount} and {@link #truncationApplied} reflect the result.
 *
 * <h3>MongoDB persistence</h3>
 * This class is stored as a nested document inside {@link com.causa.rca.model.RcaAnalysisSession}.
 * {@code Optional} is intentionally avoided here because the MongoDB BSON codec
 * cannot serialize {@code java.util.Optional}.  Use {@code null}-check on {@link #jfr} instead.
 */
@RegisterForReflection
public class CollectedArtifacts {

    // ── Identity ──────────────────────────────────────────────────────────────

    /** Kubernetes namespace of the analysed pod. */
    public String namespace;

    /** Name of the analysed pod. */
    public String podName;

    // ── Artifacts ─────────────────────────────────────────────────────────────

    /** Resource metrics (CPU, memory) from Prometheus + Kubernetes. */
    public MetricArtifact metrics;

    /** Pod phase, container states, restart counts. */
    public PodInfoArtifact podInfo;

    /** Kubernetes events filtered for this pod. */
    public EventArtifact events;

    /** Pod logs (current or previous container). */
    public LogArtifact logs;

    /**
     * Optional JFR analysis from Cryostat.
     * Null when {@code cryostat.enabled=false} or no recording is available.
     * <p>
     * NOTE: {@code java.util.Optional} is intentionally NOT used here because
     * the MongoDB BSON codec cannot serialize it.
     * </p>
     */
    public JfrArtifact jfr;

    // ── Token budget metadata ─────────────────────────────────────────────────

    /**
     * Estimated token count of the combined LLM context AFTER preprocessing.
     * Populated by {@link com.causa.rca.utils.TokenBudgetEnforcer}.
     */
    public int tokenCount;

    /**
     * {@code true} if log lines were truncated to stay within the 4 000-token
     * hard cap.  Populated by {@link com.causa.rca.utils.TokenBudgetEnforcer}.
     */
    public boolean truncationApplied;

    public CollectedArtifacts() {
    }

    /**
     * Builds the compact LLM context string from the summaries of all artifacts.
     * <p>
     * This is the string that should be passed to LLM services.  It contains
     * ONLY summary data – never raw logs, raw events, or full JFR reports.
     * </p>
     *
     * @return a compact, LLM-safe context string
     */
    public String toLlmContext() {
        StringBuilder sb = new StringBuilder();

        sb.append("=POD_STATUS= ");
        if (podInfo != null && podInfo.summary != null) {
            sb.append(podInfo.summary);
        }
        sb.append("\n");

        sb.append("=METRICS= ");
        if (metrics != null && metrics.summary != null) {
            sb.append(metrics.summary);
        }
        sb.append("\n");

        sb.append("=EVENTS= ");
        if (events != null && events.summary != null) {
            sb.append(events.summary.toString());
        }
        sb.append("\n");

        sb.append("=LOGS= ");
        if (logs != null && logs.summary != null) {
            sb.append(logs.summary.toString());
        }
        sb.append("\n");

        if (jfr != null) {
            sb.append("=JFR= ");
            sb.append(jfr.summary != null ? jfr.summary : "unavailable");
            sb.append("\n");
        }

        if (truncationApplied) {
            sb.append("[NOTE: log lines were truncated to fit token budget]\n");
        }

        return sb.toString();
    }



    public String toAnamolyLLMContext() {
        StringBuilder sb = new StringBuilder();

        sb.append("=POD_STATUS= ");
        if (podInfo != null && podInfo.summary != null) {
            sb.append(podInfo.summary);
        }
        sb.append("\n");

        sb.append("=METRICS= ");
        if (metrics != null && metrics.summary != null) {
            sb.append(metrics.summary);
        }
        sb.append("\n");

        sb.append("=EVENTS= ");
        if (events != null && events.summary != null) {
            sb.append(events.summary.toString());
        }
        return sb.toString();
    }

    /**
     * Builds context with summarized logs for GC pause detection.
     * Uses the summarized logs from LogOptimizer (not raw logs).
     */
    public String toSummarizedLogsContext() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("=SUMMARIZED_LOGS= ");
        if (logs != null && logs.summarizedLogs != null && !logs.summarizedLogs.isEmpty()) {
            sb.append(String.join("\n", logs.summarizedLogs));
        }
        sb.append("\n");
        
        return sb.toString();
    }
}

