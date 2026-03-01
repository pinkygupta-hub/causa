package com.causa.rca.model.artifact;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Artifact holding Kubernetes event data for a pod.
 * <p>
 * Provides three layers of data:
 * <ul>
 *   <li>{@link #rawEvents} – full event list for UX display</li>
 *   <li>{@link #deduplicatedEvents} – deduplicated event strings (intermediate form)</li>
 *   <li>{@link #summary} – compact LLM-safe summary (the ONLY layer sent to LLMs)</li>
 * </ul>
 * </p>
 */
@RegisterForReflection
public class EventArtifact {

    // ── LLM-safe summary ──────────────────────────────────────────────────────

    /**
     * Compact, LLM-safe summary of events.
     * This is the ONLY field that should be passed to LLM services.
     */
    public EventSummary summary;

    // ── Intermediate form ─────────────────────────────────────────────────────

    /**
     * Deduplicated event strings (order-preserved).
     * Produced by {@link com.causa.rca.service.LogOptimizer}.
     */
    public List<String> deduplicatedEvents = new ArrayList<>();

    // ── Raw data for UX ───────────────────────────────────────────────────────

    /**
     * Full raw event list as collected from Kubernetes.
     * Never sent to LLMs; used only for UX display.
     */
    public List<RawEvent> rawEvents = new ArrayList<>();

    public EventArtifact() {
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Nested types
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * A single raw Kubernetes event entry.
     */
    @RegisterForReflection
    public static class RawEvent {

        /** ISO-8601 timestamp of the last occurrence. */
        public String lastTimestamp;

        /** Event type: "Normal" or "Warning". */
        public String type;

        /** Short reason code (e.g. "OOMKilling", "BackOff", "Pulled"). */
        public String reason;

        /** Full human-readable message. */
        public String message;

        /** Number of times this event has occurred. */
        public int count;

        public RawEvent() {
        }

        public RawEvent(String lastTimestamp, String type, String reason, String message, int count) {
            this.lastTimestamp = lastTimestamp;
            this.type          = type;
            this.reason        = reason;
            this.message       = message;
            this.count         = count;
        }

        @Override
        public String toString() {
            return String.format("[%s] type=%s reason=%s count=%d msg=%s",
                    lastTimestamp, type, reason, count, message);
        }
    }

    /**
     * LLM-safe summary of events, grouped by reason with counts and recent examples.
     */
    @RegisterForReflection
    public static class EventSummary {

        /** Total number of raw events (before deduplication). */
        public int totalRawCount;

        /** Number of events after deduplication. */
        public int deduplicatedCount;

        /**
         * Events grouped by reason.
         * Key = reason string, Value = {@link ReasonGroup}.
         */
        public Map<String, ReasonGroup> byReason;

        /**
         * The most recent Warning events (up to 5), ordered newest-first.
         * Warnings are prioritised because they are most relevant for RCA.
         */
        public List<String> mostRecentWarnings = new ArrayList<>();

        public EventSummary() {
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("events(total=").append(totalRawCount)
              .append(" dedup=").append(deduplicatedCount).append(")");
            if (byReason != null && !byReason.isEmpty()) {
                sb.append(" reasons=[");
                byReason.forEach((reason, group) ->
                        sb.append(reason).append("x").append(group.count).append(" "));
                sb.append("]");
            }
            if (!mostRecentWarnings.isEmpty()) {
                sb.append(" recentWarnings=").append(mostRecentWarnings);
            }
            return sb.toString();
        }
    }

    /**
     * Aggregated data for a single event reason.
     */
    @RegisterForReflection
    public static class ReasonGroup {

        /** Reason code (e.g. "OOMKilling"). */
        public String reason;

        /** Dominant event type for this reason ("Normal" or "Warning"). */
        public String type;

        /** Total occurrence count across all events with this reason. */
        public int count;

        /** Up to 3 representative message examples. */
        public List<String> examples = new ArrayList<>();

        /** Timestamp of the most recent event with this reason. */
        public String mostRecentTimestamp;

        public ReasonGroup() {
        }

        public ReasonGroup(String reason, String type) {
            this.reason = reason;
            this.type   = type;
        }
    }
}

