package com.causa.rca.service;

import com.causa.rca.clients.CryostatClient;
import com.causa.rca.clients.PrometheusClient;
import com.causa.rca.model.artifact.CollectedArtifacts;
import com.causa.rca.model.artifact.EventArtifact;
import com.causa.rca.model.artifact.EventArtifact.RawEvent;
import com.causa.rca.model.artifact.JfrArtifact;
import com.causa.rca.model.artifact.MetricArtifact;
import com.causa.rca.model.artifact.PodInfoArtifact;
import com.causa.rca.model.artifact.PodInfoArtifact.ContainerInfo;
import com.causa.rca.utils.LogOptimizer;
import com.causa.rca.utils.TokenBudgetEnforcer;
import com.causa.rca.utils.TokenProvider;
import com.fasterxml.jackson.databind.JsonNode;

import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service responsible for collecting diagnostic data from various sources and packaging
 * it into a structured {@link CollectedArtifacts} object.
 *
 * <h3>Data sources</h3>
 * <ul>
 *   <li>Prometheus – CPU and memory metrics</li>
 *   <li>Kubernetes API – pod status, container states, events</li>
 *   <li>Kubernetes logs – current and previous container logs</li>
 *   <li>Cryostat – optional JFR analysis (only when {@code cryostat.enabled=true})</li>
 * </ul>
 *
 * <h3>Output contract</h3>
 * <ul>
 *   <li>Each artifact separates <b>raw data</b> (for UX) from <b>LLM-safe summaries</b>.</li>
 *   <li>LLM services must only consume {@link CollectedArtifacts#toLlmContext()}.</li>
 *   <li>Raw data is preserved in each artifact for dashboard / UX display.</li>
 *   <li>Token budget is enforced by {@link TokenBudgetEnforcer} (hard cap: 4 000 tokens).</li>
 * </ul>
 *
 * @see LogOptimizer
 * @see TokenBudgetEnforcer
 * @see CollectedArtifacts
 */
@ApplicationScoped
public class DataCollectorService {

    private static final Logger LOG = Logger.getLogger(DataCollectorService.class);

    @Inject
    KubernetesClient kubernetesClient;

    @Inject
    @RestClient
    PrometheusClient prometheusClient;

    @Inject
    @RestClient
    CryostatClient cryostatClient;

    @Inject
    TokenProvider tokenProvider;

    @Inject
    LogOptimizer logOptimizer;

    @Inject
    TokenBudgetEnforcer tokenBudgetEnforcer;

    @ConfigProperty(name = "cryostat.enabled", defaultValue = "false")
    boolean cryostatEnabled;

    // ─────────────────────────────────────────────────────────────────────────
    // Primary entry point
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Collects all diagnostic data for a pod and returns a structured
     * {@link CollectedArtifacts} object ready for LLM consumption and UX display.
     *
     * <p>Steps performed:
     * <ol>
     *   <li>Fetch pod status from Kubernetes API → {@link PodInfoArtifact}</li>
     *   <li>Fetch Kubernetes events → {@link EventArtifact} (with dedup + summary)</li>
     *   <li>Fetch Prometheus metrics → {@link MetricArtifact}</li>
     *   <li>Fetch pod logs → {@link com.causa.rca.model.artifact.LogArtifact} (with dedup + summary)</li>
     *   <li>Optionally fetch JFR from Cryostat → {@link JfrArtifact}</li>
     *   <li>Enforce 4 000-token budget via {@link TokenBudgetEnforcer}</li>
     * </ol>
     * </p>
     *
     * @param namespace the Kubernetes namespace of the pod
     * @param podName   the name of the pod
     * @return a fully populated {@link CollectedArtifacts}
     */
    public CollectedArtifacts collectArtifacts(String namespace, String podName) {
        LOG.info("Starting artifact collection for: " + namespace + "/" + podName);

        CollectedArtifacts artifacts = new CollectedArtifacts();
        artifacts.namespace = namespace;
        artifacts.podName   = podName;

        // 1. Pod status
        artifacts.podInfo = fetchPodInfoArtifact(namespace, podName);

        // 2. Events
        artifacts.events = fetchEventArtifact(namespace, podName);

        // 3. Metrics
        artifacts.metrics = fetchMetricArtifact(namespace, podName);

        // 4. Logs
        String rawLogText = fetchRawLogs(namespace, podName);
        artifacts.logs = logOptimizer.processLogs(rawLogText);

        // 5. JFR (optional – only when cryostat.enabled=true)
        if (cryostatEnabled) {
            artifacts.jfr = fetchJfrArtifact(podName);
        } else {
            LOG.info("Cryostat disabled – skipping JFR collection.");
            artifacts.jfr = null;
        }

        // 6. Enforce token budget (may truncate log representative lines)
        tokenBudgetEnforcer.enforce(artifacts);

        LOG.info("Artifact collection complete for " + podName
                + " | tokens=" + artifacts.tokenCount
                + " | truncated=" + artifacts.truncationApplied);

        return artifacts;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pod info
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fetches pod phase and container states from the Kubernetes API.
     *
     * @param namespace the Kubernetes namespace
     * @param podName   the pod name
     * @return a populated {@link PodInfoArtifact}
     */
    public PodInfoArtifact fetchPodInfoArtifact(String namespace, String podName) {
        LOG.info("Fetching pod info for: " + namespace + "/" + podName);
        PodInfoArtifact artifact = new PodInfoArtifact();
        artifact.namespace = namespace;
        artifact.podName   = podName;

        try {
            Pod pod = kubernetesClient.pods().inNamespace(namespace).withName(podName).get();
            if (pod == null) {
                artifact.phase = "NotFound";
                artifact.rawStatusText = "Pod not found";
                artifact.buildSummary();
                return artifact;
            }

            artifact.phase = pod.getStatus().getPhase();

            // Build raw status text (preserved for UX)
            StringBuilder rawStatus = new StringBuilder();
            rawStatus.append("Phase: ").append(artifact.phase).append("\n");

            List<ContainerStatus> containerStatuses = pod.getStatus().getContainerStatuses();
            if (containerStatuses != null) {
                for (ContainerStatus cs : containerStatuses) {
                    ContainerInfo ci = new ContainerInfo();
                    ci.name         = cs.getName();
                    ci.ready        = Boolean.TRUE.equals(cs.getReady());
                    ci.restartCount = cs.getRestartCount() != null ? cs.getRestartCount() : 0;

                    rawStatus.append("Container: ").append(ci.name).append("\n");
                    rawStatus.append("  Ready: ").append(ci.ready).append("\n");
                    rawStatus.append("  Restart Count: ").append(ci.restartCount).append("\n");

                    // Current state
                    if (cs.getState() != null) {
                        if (cs.getState().getRunning() != null) {
                            ci.currentState = "Running";
                        } else if (cs.getState().getWaiting() != null) {
                            String reason = cs.getState().getWaiting().getReason();
                            ci.currentState    = "Waiting(" + reason + ")";
                            ci.waitingMessage  = cs.getState().getWaiting().getMessage();
                            rawStatus.append("  Current State: Waiting (").append(reason).append(")\n");
                            rawStatus.append("  Message: ").append(ci.waitingMessage).append("\n");
                        } else if (cs.getState().getTerminated() != null) {
                            ci.currentState = "Terminated(" + cs.getState().getTerminated().getReason() + ")";
                        }
                    }

                    // Last terminated state
                    if (cs.getLastState() != null && cs.getLastState().getTerminated() != null) {
                        ci.lastTerminatedReason = cs.getLastState().getTerminated().getReason();
                        ci.lastExitCode         = cs.getLastState().getTerminated().getExitCode();
                        ci.lastFinishedAt       = cs.getLastState().getTerminated().getFinishedAt();
                        rawStatus.append("  Last State: Terminated (").append(ci.lastTerminatedReason).append(")\n");
                        rawStatus.append("  Exit Code: ").append(ci.lastExitCode).append("\n");
                        rawStatus.append("  Finished At: ").append(ci.lastFinishedAt).append("\n");
                    }

                    artifact.containers.add(ci);
                }
            }

            artifact.rawStatusText = rawStatus.toString();
            artifact.buildSummary();

            LOG.info("Pod info collected: " + artifact.summary);
        } catch (Exception e) {
            LOG.error("Failed to fetch pod info for " + podName, e);
            artifact.phase         = "Error";
            artifact.rawStatusText = "Error fetching pod status: " + e.getMessage();
            artifact.buildSummary();
        }

        return artifact;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Events
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fetches Kubernetes events for the pod and processes them into an {@link EventArtifact}.
     *
     * @param namespace the Kubernetes namespace
     * @param podName   the pod name
     * @return a populated {@link EventArtifact} with raw, dedup, and summary layers
     */
    public EventArtifact fetchEventArtifact(String namespace, String podName) {
        LOG.info("Fetching K8s events for pod: " + podName);
        try {
            // Derive the pod name prefix for matching ReplicaSet/Deployment-owned pods.
            // Pod names follow the pattern: <deployment>-<rs-hash>-<pod-hash>
            // We match events whose involvedObject.name equals the pod name OR starts with
            // the pod name (covers events emitted against the pod itself) OR whose
            // involvedObject.name is a prefix of the pod name (covers ReplicaSet events).
            String podPrefix = derivePodPrefix(podName);

            List<Event> k8sEvents = kubernetesClient.v1().events()
                    .inNamespace(namespace).list().getItems().stream()
                    .filter(e -> {
                        if (e.getInvolvedObject() == null) return false;
                        String objName = e.getInvolvedObject().getName();
                        if (objName == null) return false;
                        // Exact match (pod itself)
                        if (podName.equals(objName)) return true;
                        // Pod name starts with the object name (e.g. ReplicaSet is prefix of pod)
                        if (podName.startsWith(objName + "-")) return true;
                        // Object name starts with pod prefix (e.g. another pod in same RS)
                        if (!podPrefix.isEmpty() && objName.startsWith(podPrefix)) return true;
                        return false;
                    })
                    .collect(Collectors.toList());

            LOG.info("Gathered " + k8sEvents.size() + " raw events for " + podName
                    + " (prefix=" + podPrefix + ")");

            // Map to RawEvent value objects
            List<RawEvent> rawEvents = new ArrayList<>();
            for (Event e : k8sEvents) {
                int count = (e.getCount() != null) ? e.getCount() : 1;
                rawEvents.add(new RawEvent(
                        e.getLastTimestamp(),
                        e.getType(),
                        e.getReason(),
                        e.getMessage(),
                        count));
            }

            return logOptimizer.processEvents(rawEvents);

        } catch (Exception e) {
            LOG.error("Failed to fetch events for " + podName, e);
            // Return empty artifact with error note
            EventArtifact artifact = new EventArtifact();
            artifact.rawEvents          = List.of();
            artifact.deduplicatedEvents = List.of();
            EventArtifact.EventSummary summary = new EventArtifact.EventSummary();
            summary.totalRawCount      = 0;
            summary.deduplicatedCount  = 0;
            summary.byReason           = java.util.Map.of();
            summary.mostRecentWarnings = List.of("Error fetching events: " + e.getMessage());
            artifact.summary = summary;
            return artifact;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Metrics
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fetches CPU and memory metrics from Prometheus and Kubernetes resource specs,
     * returning a structured {@link MetricArtifact}.
     *
     * @param namespace the Kubernetes namespace
     * @param podName   the pod name
     * @return a populated {@link MetricArtifact}
     */
    public MetricArtifact fetchMetricArtifact(String namespace, String podName) {
        LOG.info("Fetching metrics for: " + namespace + "/" + podName);
        try {
            // 1. K8s resource config
            Pod pod = kubernetesClient.pods().inNamespace(namespace).withName(podName).get();
            String k8sLimits   = "N/A";
            String k8sRequests = "N/A";
            if (pod != null && pod.getSpec() != null && !pod.getSpec().getContainers().isEmpty()) {
                var container = pod.getSpec().getContainers().get(0);
                if (container.getResources() != null) {
                    if (container.getResources().getLimits() != null) {
                        k8sLimits = container.getResources().getLimits().toString()
                                .replace("{", "[").replace("}", "]");
                    }
                    if (container.getResources().getRequests() != null) {
                        k8sRequests = container.getResources().getRequests().toString()
                                .replace("{", "[").replace("}", "]");
                    }
                }
            }

            // 2. PromQL queries
            String currentMemQuery = String.format(
                    "sum(container_memory_usage_bytes{pod=\"%s\", namespace=\"%s\", container!=\"\", image!=\"\"})",
                    podName, namespace);
            String limitMemQuery = String.format(
                    "sum(container_spec_memory_limit_bytes{pod=\"%s\", namespace=\"%s\", container!=\"\", image!=\"\"})",
                    podName, namespace);
            String currentCpuQuery = String.format(
                    "sum(rate(container_cpu_usage_seconds_total{pod=\"%s\", namespace=\"%s\", container!=\"\", image!=\"\"}[5m]))",
                    podName, namespace);
            String limitCpuQuery = String.format(
                    "sum(container_spec_cpu_quota{pod=\"%s\", namespace=\"%s\", container!=\"\", image!=\"\"}) / "
                    + "sum(container_spec_cpu_period{pod=\"%s\", namespace=\"%s\", container!=\"\", image!=\"\"})",
                    podName, namespace, podName, namespace);

            double memUsageBytes = extractValue(prometheusClient.query(tokenProvider.getToken(), currentMemQuery));
            double memLimitBytes = extractValue(prometheusClient.query(tokenProvider.getToken(), limitMemQuery));
            double cpuUsageCores = extractValue(prometheusClient.query(tokenProvider.getToken(), currentCpuQuery));
            double cpuLimitCores = extractValue(prometheusClient.query(tokenProvider.getToken(), limitCpuQuery));

            LOG.info(String.format(
                    "Metrics extracted – mem=%.0f/%.0f cpu=%.3f/%.3f",
                    memUsageBytes, memLimitBytes, cpuUsageCores, cpuLimitCores));

            // 3. JVM fallback if container metrics are zero
            boolean jvmFallback = false;
            if (memUsageBytes == 0.0) {
                LOG.info("Container memory metrics = 0, attempting JVM heap fallback...");
                String jvmMemQuery = String.format(
                        "sum(jvm_memory_used_bytes{pod=\"%s\", namespace=\"%s\", area=\"heap\"})",
                        podName, namespace);
                memUsageBytes = extractValue(prometheusClient.query(tokenProvider.getToken(), jvmMemQuery));
                jvmFallback   = true;
                LOG.info("JVM fallback mem usage: " + memUsageBytes);
            }

            return MetricArtifact.of(memUsageBytes, memLimitBytes, cpuUsageCores, cpuLimitCores,
                    k8sLimits, k8sRequests, jvmFallback);

        } catch (Exception e) {
            LOG.error("Failed to fetch metrics for " + podName, e);
            // Return a minimal error artifact so the pipeline can continue
            MetricArtifact err = new MetricArtifact();
            err.summary = "metrics-unavailable: " + e.getMessage();
            return err;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Logs
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fetches raw pod logs from Kubernetes (last 500 lines).
     * Falls back to previous container logs if current logs are empty.
     *
     * @param namespace the Kubernetes namespace
     * @param podName   the pod name
     * @return the raw log text (newline-separated), or an empty string on error
     */
    public String fetchRawLogs(String namespace, String podName) {
        LOG.info("Fetching logs for pod: " + podName);
        try {
            String logs = kubernetesClient.pods()
                    .inNamespace(namespace).withName(podName)
                    .tailingLines(500).getLog();

            if (logs == null || logs.trim().isEmpty()) {
                LOG.info("Current logs empty, attempting previous container logs for: " + podName);
                logs = kubernetesClient.pods()
                        .inNamespace(namespace).withName(podName)
                        .terminated().tailingLines(500).getLog();
            }

            LOG.info("Gathered logs (length: " + (logs != null ? logs.length() : 0) + ")");
            return (logs != null && !logs.isEmpty()) ? logs : "";

        } catch (Exception e) {
            LOG.error("Failed to fetch pod logs for " + podName, e);
            return "";
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JFR
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fetches the JFR analysis report from Cryostat and wraps it in a {@link JfrArtifact}.
     * Only called when {@code cryostat.enabled=true}.
     *
     * @param podName the pod name (used as the Cryostat target identifier)
     * @return a populated {@link JfrArtifact}
     */
    public JfrArtifact fetchJfrArtifact(String podName) {
        LOG.info("Fetching JFR analysis from Cryostat for: " + podName);
        try {
            String report = cryostatClient.getReport(tokenProvider.getToken(), podName);
            LOG.info("JFR report fetched (length: " + (report != null ? report.length() : 0) + ")");
            return JfrArtifact.of(podName, report);
        } catch (Exception e) {
            LOG.error("Failed to fetch JFR report for " + podName, e);
            return JfrArtifact.of(podName, null);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Derives the deployment/ReplicaSet name prefix from a pod name.
     * <p>
     * Kubernetes pod names follow the pattern: {@code <deployment>-<rs-hash>-<pod-hash>}
     * where both hashes are 5-character alphanumeric suffixes.
     * This method strips the last two dash-separated segments to get the deployment prefix,
     * which is used to match events emitted against the ReplicaSet or Deployment.
     * </p>
     * <p>
     * Example: {@code my-app-7d9f8b6c4-xk2pq} → {@code my-app}
     * </p>
     *
     * @param podName the full pod name
     * @return the deployment prefix, or empty string if the name has fewer than 3 segments
     */
    private String derivePodPrefix(String podName) {
        if (podName == null || podName.isEmpty()) return "";
        String[] parts = podName.split("-");
        // Need at least 3 parts: <name>-<rs-hash>-<pod-hash>
        if (parts.length < 3) return "";
        // Rejoin all parts except the last two
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < parts.length - 2; i++) {
            if (i > 0) prefix.append('-');
            prefix.append(parts[i]);
        }
        return prefix.toString();
    }

    /**
     * Extracts a numeric value from a Prometheus instant-query response.
     *
     * @param result the Prometheus JSON response node
     * @return the extracted value, or 0.0 if absent / malformed
     */
    private double extractValue(JsonNode result) {
        try {
            if (result != null
                    && result.has("data")
                    && result.get("data").has("result")
                    && result.get("data").get("result").size() > 0) {
                return result.get("data").get("result").get(0).get("value").get(1).asDouble();
            }
        } catch (Exception e) {
            LOG.warn("Could not extract value from Prometheus response: " + e.getMessage());
        }
        return 0.0;
    }
}

// Made with Bob
