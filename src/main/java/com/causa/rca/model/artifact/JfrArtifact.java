package com.causa.rca.model.artifact;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Optional artifact holding Java Flight Recorder (JFR) analysis data.
 * <p>
 * JFR data is NOT fetched by default. It is only populated when Cryostat is
 * enabled ({@code cryostat.enabled=true}) and a recording is available.
 * </p>
 * <p>
 * Separates the LLM-safe summary from the full raw report so that UX layers
 * can display the complete JFR report while LLMs only receive the compact summary.
 * </p>
 */
@RegisterForReflection
public class JfrArtifact {

    // ── LLM-safe summary ──────────────────────────────────────────────────────

    /**
     * Compact, LLM-safe summary extracted from the JFR report.
     * This is the ONLY field that should be passed to LLM services.
     * Contains key JVM metrics: GC pressure, top allocators, thread contention, etc.
     */
    public String summary;

    // ── Raw data for UX ───────────────────────────────────────────────────────

    /**
     * Full raw JFR report text as returned by Cryostat.
     * Never sent to LLMs; used only for UX display.
     */
    public String rawReport;

    /** Target identifier used to fetch the report (typically the pod name). */
    public String target;

    /** Length of the raw report in characters (for quick size checks). */
    public int rawReportLength;

    public JfrArtifact() {
    }

    /**
     * Factory method that creates a JfrArtifact from a raw Cryostat report.
     * The summary is derived by taking the first 1000 characters of the report
     * (a heuristic that captures the most important JVM metrics at the top).
     *
     * @param target    the pod/target name
     * @param rawReport the full JFR report text from Cryostat
     * @return a populated JfrArtifact
     */
    public static JfrArtifact of(String target, String rawReport) {
        JfrArtifact a = new JfrArtifact();
        a.target          = target;
        a.rawReport       = rawReport;
        a.rawReportLength = rawReport != null ? rawReport.length() : 0;

        // Build a compact summary: first 1000 chars covers the executive summary
        // section that Cryostat typically places at the top of its HTML/text reports.
        if (rawReport != null && !rawReport.isBlank()) {
            String stripped = rawReport.replaceAll("<[^>]+>", " ")  // strip HTML tags
                                       .replaceAll("\\s{2,}", " ")  // collapse whitespace
                                       .trim();
            a.summary = stripped.length() > 1000
                    ? stripped.substring(0, 1000) + "...[truncated]"
                    : stripped;
        } else {
            a.summary = "JFR report unavailable.";
        }
        return a;
    }
}

