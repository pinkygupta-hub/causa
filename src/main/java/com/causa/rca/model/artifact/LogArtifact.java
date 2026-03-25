package com.causa.rca.model.artifact;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.ArrayList;
import java.util.List;

/**
 * Artifact holding pod log data.
 * <p>
 * Provides three layers of data:
 * <ul>
 *   <li>{@link #rawLines} – full original log lines for UX display</li>
 *   <li>{@link #deduplicatedLines} – deduplicated, normalized lines (intermediate form)</li>
 *   <li>{@link #summary} – compact LLM-safe summary (the ONLY layer sent to LLMs)</li>
 * </ul>
 * </p>
 * <p>
 * LLMs must ONLY consume {@link LogSummary}, never raw or deduplicated lines directly.
 * </p>
 */
@RegisterForReflection
public class LogArtifact {

    // ── LLM-safe summary ──────────────────────────────────────────────────────

    /**
     * Compact, LLM-safe summary of log content.
     * This is the ONLY field that should be passed to LLM services.
     */
    public LogSummary summary;

    // ── Intermediate form ─────────────────────────────────────────────────────

    /**
     * Deduplicated, normalized log lines (order-preserved).
     * Produced by {@link com.causa.rca.service.LogOptimizer}.
     * Not sent to LLMs; available for intermediate processing or UX.
     */
    public List<String> deduplicatedLines = new ArrayList<>();

    /**
     * Summarized log lines generated from raw logs.
     * Provides a condensed view of the logs for analysis.
     */
    public List<String> summarizedLogs = new ArrayList<>();

    // ── Raw data for UX ───────────────────────────────────────────────────────

    /**
     * Full original log lines as fetched from Kubernetes.
     * Never sent to LLMs; used only for UX display.
     */
    public List<String> rawLines = new ArrayList<>();

    public LogArtifact() {
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Getters and Setters
    // ─────────────────────────────────────────────────────────────────────────

    public List<String> getSummarizedLogs() {
        return summarizedLogs;
    }

    public void setSummarizedLogs(List<String> summarizedLogs) {
        this.summarizedLogs = summarizedLogs;
    }

    public List<String> getDeduplicatedLines() {
        return deduplicatedLines;
    }

    public void setDeduplicatedLines(List<String> deduplicatedLines) {
        this.deduplicatedLines = deduplicatedLines;
    }

    public List<String> getRawLines() {
        return rawLines;
    }

    public void setRawLines(List<String> rawLines) {
        this.rawLines = rawLines;
    }

    public LogSummary getSummary() {
        return summary;
    }

    public void setSummary(LogSummary summary) {
        this.summary = summary;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Nested types
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * LLM-safe summary of log content.
     * <p>
     * Contains counts by severity and a prioritised list of representative lines
     * (errors first, then warnings, then info) weighted by frequency.
     * </p>
     */
    @RegisterForReflection
    public static class LogSummary {

        /** Total number of raw log lines (before deduplication). */
        public int totalRawLines;

        /** Number of lines after deduplication. */
        public int deduplicatedLineCount;

        /** Number of lines containing ERROR or SEVERE. */
        public int errorCount;

        /** Number of lines containing WARN or WARNING. */
        public int warnCount;

        /** Number of lines containing INFO. */
        public int infoCount;

        /**
         * Representative log lines selected by priority:
         * <ol>
         *   <li>ERROR lines (highest weight)</li>
         *   <li>WARN lines</li>
         *   <li>INFO lines (lowest weight)</li>
         * </ol>
         * Repeated patterns add weight; top-N are selected.
         * Maximum 20 lines to stay within token budget.
         */
        public List<String> representativeLines = new ArrayList<>();

        public LogSummary() {
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("logs(total=").append(totalRawLines)
              .append(" dedup=").append(deduplicatedLineCount)
              .append(" errors=").append(errorCount)
              .append(" warns=").append(warnCount)
              .append(" infos=").append(infoCount)
              .append(") topLines=").append(representativeLines);
            return sb.toString();
        }
    }
}

