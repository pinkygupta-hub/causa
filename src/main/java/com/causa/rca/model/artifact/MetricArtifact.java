package com.causa.rca.model.artifact;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Artifact holding resource metric data for a pod.
 * <p>
 * Contains both the LLM-safe summary string (used by AI services) and the
 * raw structured metric values (used by UX/display layers).
 * </p>
 */
@RegisterForReflection
public class MetricArtifact {

    // ── LLM-safe summary ──────────────────────────────────────────────────────

    /**
     * Compact, LLM-safe summary of resource metrics.
     * This is the ONLY field that should be passed to LLM services.
     * Example: "mem=512MB/1024MB(50%) cpu=0.5c/1c(50%)"
     */
    public String summary;

    // ── Raw values for UX ─────────────────────────────────────────────────────

    /** Memory usage in bytes (raw Prometheus value). */
    public double memoryUsageBytes;

    /** Memory limit in bytes (raw Prometheus value). */
    public double memoryLimitBytes;

    /** Memory usage as a percentage of the limit (0–100). */
    public double memoryUsagePercent;

    /** CPU usage in cores (raw Prometheus value). */
    public double cpuUsageCores;

    /** CPU limit in cores (raw Prometheus value). */
    public double cpuLimitCores;

    /** CPU usage as a percentage of the limit (0–100). */
    public double cpuUsagePercent;

    /** Raw Kubernetes resource limits string (e.g. "{memory=1Gi, cpu=1}"). */
    public String k8sLimits;

    /** Raw Kubernetes resource requests string. */
    public String k8sRequests;

    /** True if the JVM heap fallback metric was used instead of container metrics. */
    public boolean jvmFallbackUsed;

    public MetricArtifact() {
    }

    /**
     * Convenience factory that builds the LLM summary from the populated raw fields.
     */
    public static MetricArtifact of(
            double memUsageBytes, double memLimitBytes,
            double cpuUsageCores, double cpuLimitCores,
            String k8sLimits, String k8sRequests,
            boolean jvmFallbackUsed) {

        MetricArtifact a = new MetricArtifact();
        a.memoryUsageBytes   = memUsageBytes;
        a.memoryLimitBytes   = memLimitBytes;
        a.cpuUsageCores      = cpuUsageCores;
        a.cpuLimitCores      = cpuLimitCores;
        a.k8sLimits          = k8sLimits;
        a.k8sRequests        = k8sRequests;
        a.jvmFallbackUsed    = jvmFallbackUsed;

        a.memoryUsagePercent = (memLimitBytes > 0) ? (memUsageBytes / memLimitBytes) * 100 : 0;
        a.cpuUsagePercent    = (cpuLimitCores > 0) ? (cpuUsageCores / cpuLimitCores) * 100 : 0;

        double memMB      = memUsageBytes  / (1024 * 1024);
        double memLimitMB = memLimitBytes  / (1024 * 1024);

        a.summary = String.format(
                "mem=%.0fMB/%.0fMB(%.1f%%) cpu=%.3fc/%.3fc(%.1f%%)%s",
                memMB, memLimitMB, a.memoryUsagePercent,
                cpuUsageCores, cpuLimitCores, a.cpuUsagePercent,
                jvmFallbackUsed ? " [jvm-fallback]" : "");

        return a;
    }
}

