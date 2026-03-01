package com.causa.rca.model.artifact;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.ArrayList;
import java.util.List;

/**
 * Artifact holding Kubernetes pod status and container state information.
 * <p>
 * Separates the LLM-safe summary from the full raw status detail so that
 * UX layers can display the complete picture while LLMs only receive the
 * compact summary.
 * </p>
 */
@RegisterForReflection
public class PodInfoArtifact {

    // ── LLM-safe summary ──────────────────────────────────────────────────────

    /**
     * Compact, LLM-safe summary of pod status.
     * This is the ONLY field that should be passed to LLM services.
     * Example: "phase=Running containers=[app: ready=true restarts=3 state=Running lastState=OOMKilled(137)]"
     */
    public String summary;

    // ── Raw data for UX ───────────────────────────────────────────────────────

    /** Pod phase as reported by Kubernetes (Running, Pending, Failed, Succeeded, Unknown). */
    public String phase;

    /** Namespace the pod belongs to. */
    public String namespace;

    /** Pod name. */
    public String podName;

    /** Detailed container status entries, one per container. */
    public List<ContainerInfo> containers = new ArrayList<>();

    /** Full raw status text as originally formatted (for UX display). */
    public String rawStatusText;

    public PodInfoArtifact() {
    }

    /**
     * Immutable value object representing the status of a single container.
     */
    @RegisterForReflection
    public static class ContainerInfo {

        /** Container name. */
        public String name;

        /** Whether the container is ready. */
        public boolean ready;

        /** Number of restarts. */
        public int restartCount;

        /** Current state: "Running", "Waiting(<reason>)", "Terminated(<reason>)" */
        public String currentState;

        /** Optional message for Waiting state. */
        public String waitingMessage;

        /** Last terminated reason (e.g. "OOMKilled"). */
        public String lastTerminatedReason;

        /** Exit code of the last termination. */
        public Integer lastExitCode;

        /** Timestamp when the last termination finished. */
        public String lastFinishedAt;

        public ContainerInfo() {
        }
    }

    /**
     * Builds the compact LLM summary from the populated raw fields and stores it
     * in {@link #summary}.  Call this after all containers have been added.
     */
    public void buildSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("phase=").append(phase != null ? phase : "unknown");
        sb.append(" containers=[");
        for (int i = 0; i < containers.size(); i++) {
            ContainerInfo c = containers.get(i);
            if (i > 0) sb.append(", ");
            sb.append(c.name)
              .append(": ready=").append(c.ready)
              .append(" restarts=").append(c.restartCount)
              .append(" state=").append(c.currentState != null ? c.currentState : "unknown");
            if (c.lastTerminatedReason != null) {
                sb.append(" lastState=").append(c.lastTerminatedReason);
                if (c.lastExitCode != null) {
                    sb.append("(").append(c.lastExitCode).append(")");
                }
            }
        }
        sb.append("]");
        this.summary = sb.toString();
    }
}

// Made with Bob
