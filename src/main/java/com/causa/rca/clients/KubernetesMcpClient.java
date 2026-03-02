package com.causa.rca.clients;

import com.causa.rca.clients.mcp.Langchain4jMcpService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Hybrid Kubernetes client that supports both MCP server and direct Fabric8 access.
 * <p>
 * This service provides a unified interface for Kubernetes operations with automatic
 * fallback capability:
 * <ul>
 *   <li><b>Primary:</b> Uses MCP server when enabled and available</li>
 *   <li><b>Fallback:</b> Uses direct Fabric8 Kubernetes client if MCP is disabled or fails</li>
 * </ul>
 * </p>
 * <p>
 * The hybrid approach ensures:
 * <ul>
 *   <li>Gradual migration path from direct K8s API to MCP</li>
 *   <li>High availability through fallback mechanism</li>
 *   <li>No breaking changes to existing functionality</li>
 *   <li>Easy testing and validation of MCP integration</li>
 *   <li>Production-ready REST-based transport</li>
 * </ul>
 * </p>
 *
 * @see KubernetesClient
 * @see Langchain4jMcpService
 */
@ApplicationScoped
public class KubernetesMcpClient {

    private static final Logger LOG = Logger.getLogger(KubernetesMcpClient.class);

    @Inject
    Langchain4jMcpService mcpService;

    @Inject
    KubernetesClient kubernetesClient;

    private final ObjectMapper yamlMapper = new ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());

    /**
     * Get pod information with MCP fallback to Fabric8.
     *
     * @param namespace the Kubernetes namespace
     * @param podName the pod name
     * @return Pod object
     */
    public Pod getPod(String namespace, String podName) {
        if (mcpService.isReady()) {
            try {
                LOG.infof("Using MCP for pod: %s/%s", namespace, podName);
                String podYaml = mcpService.getPod(namespace, podName);
                return yamlMapper.readValue(podYaml, Pod.class);
            } catch (Exception e) {
                LOG.warnf("MCP call failed for pod %s/%s: %s. Falling back to Fabric8.",
                         namespace, podName, e.getMessage());
            }
        }
        LOG.infof("Using Fabric8 for pod: %s/%s", namespace, podName);
        return getPodViaFabric8(namespace, podName);
    }

    /**
     * Get pod logs with MCP fallback to Fabric8.
     *
     * @param namespace the Kubernetes namespace
     * @param podName the pod name
     * @param tailLines number of lines to tail
     * @param previous whether to get logs from previous container
     * @return pod logs as string
     */
    public String getPodLogs(String namespace, String podName, int tailLines, boolean previous) {
        if (mcpService.isReady()) {
            try {
                LOG.infof("Using MCP for logs: %s/%s (tail=%d, previous=%b)", namespace, podName, tailLines, previous);
                return mcpService.getPodLogs(namespace, podName, tailLines, previous);
            } catch (Exception e) {
                LOG.warnf("MCP call failed for logs %s/%s: %s. Falling back to Fabric8.",
                         namespace, podName, e.getMessage());
            }
        }
        LOG.infof("Using Fabric8 for logs: %s/%s", namespace, podName);
        return getLogsViaFabric8(namespace, podName, tailLines, previous);
    }

    /**
     * Get pod events with MCP fallback to Fabric8.
     *
     * @param namespace the Kubernetes namespace
     * @param podName the pod name
     * @return list of events
     */
    public List<Event> getPodEvents(String namespace, String podName) {
        if (mcpService.isReady()) {
            try {
                LOG.infof("Using MCP for events: %s/%s", namespace, podName);
                // events_list returns a custom text format (not a K8s EventList JSON/YAML)
                // Filter client-side by InvolvedObject.Name matching podName
                String eventsText = mcpService.listEvents(namespace);
                return parseEventsFromMcpText(eventsText, podName);
            } catch (Exception e) {
                LOG.warnf("MCP call failed for events %s/%s: %s. Falling back to Fabric8.",
                         namespace, podName, e.getMessage());
            }
        }
        LOG.infof("Using Fabric8 for events: %s/%s", namespace, podName);
        return getEventsViaFabric8(namespace, podName);
    }

    /**
     * Get pod status with MCP fallback to Fabric8.
     *
     * @param namespace the Kubernetes namespace
     * @param podName the pod name
     * @return formatted pod status string
     */
    public String getPodStatus(String namespace, String podName) {
        if (mcpService.isReady()) {
            try {
                LOG.infof("Using MCP for status: %s/%s", namespace, podName);
                // getPodStatus returns full pod YAML (same as pods_get); parse with yamlMapper
                String podYaml = mcpService.getPodStatus(namespace, podName);
                JsonNode podNode = yamlMapper.readTree(podYaml);
                return formatPodStatusFromJson(podNode);
            } catch (Exception e) {
                LOG.warnf("MCP call failed for status %s/%s: %s. Falling back to Fabric8.",
                         namespace, podName, e.getMessage());
            }
        }
        LOG.infof("Using Fabric8 for status: %s/%s", namespace, podName);
        return getStatusViaFabric8(namespace, podName);
    }

    /**
     * List all pods across all namespaces, using MCP if available, falling back to Fabric8.
     *
     * <p>The MCP {@code pods_list} tool returns a multi-space-separated table that includes
     * a LABELS column.  The table is parsed by {@link #parsePodsFromMcpText} into lightweight
     * {@link Pod} objects containing namespace, name, phase, restart count, and labels.
     *
     * @return list of all pods
     */
    public List<Pod> listAllPods() {
        if (mcpService.isReady()) {
            try {
                LOG.info("Using MCP to list all pods");
                String podsText = mcpService.listPods(null);
                return parsePodsFromMcpText(podsText);
            } catch (Exception e) {
                LOG.warnf("MCP call failed for listAllPods: %s. Falling back to Fabric8.", e.getMessage());
            }
        }
        LOG.info("Using Fabric8 to list all pods");
        return listAllPodsViaFabric8();
    }

    /**
     * List pods with a specific label across all namespaces, using MCP if available,
     * falling back to Fabric8.
     *
     * <p>The MCP {@code pods_list} table includes a LABELS column, so client-side
     * label filtering is possible.  The full pod list is fetched and filtered in-memory.
     *
     * @param labelKey   the label key (e.g. {@code "kruize/rca"})
     * @param labelValue the label value (e.g. {@code "enabled"})
     * @return list of pods matching the label
     */
    public List<Pod> listPodsWithLabel(String labelKey, String labelValue) {
        if (mcpService.isReady()) {
            try {
                LOG.infof("Using MCP to list pods with label %s=%s (client-side filter)", labelKey, labelValue);
                String podsText = mcpService.listPods(null);
                List<Pod> allPods = parsePodsFromMcpText(podsText);
                return allPods.stream()
                        .filter(p -> p.getMetadata() != null
                                && p.getMetadata().getLabels() != null
                                && labelValue.equals(p.getMetadata().getLabels().get(labelKey)))
                        .collect(Collectors.toList());
            } catch (Exception e) {
                LOG.warnf("MCP call failed for listPodsWithLabel %s=%s: %s. Falling back to Fabric8.",
                         labelKey, labelValue, e.getMessage());
            }
        }
        LOG.infof("Using Fabric8 to list pods with label %s=%s", labelKey, labelValue);
        return listPodsWithLabelViaFabric8(labelKey, labelValue);
    }

    /**
     * List pods with a specific label in a given namespace, using MCP if available,
     * falling back to Fabric8.
     *
     * @param namespace  the Kubernetes namespace
     * @param labelKey   the label key
     * @param labelValue the label value
     * @return list of pods matching the label in the namespace
     */
    public List<Pod> listPodsWithLabelInNamespace(String namespace, String labelKey, String labelValue) {
        if (mcpService.isReady()) {
            try {
                LOG.infof("Using MCP to list pods in %s with label %s=%s (client-side filter)",
                         namespace, labelKey, labelValue);
                String podsText = mcpService.listPods(namespace);
                List<Pod> namespacePods = parsePodsFromMcpText(podsText);
                return namespacePods.stream()
                        .filter(p -> p.getMetadata() != null
                                && p.getMetadata().getLabels() != null
                                && labelValue.equals(p.getMetadata().getLabels().get(labelKey)))
                        .collect(Collectors.toList());
            } catch (Exception e) {
                LOG.warnf("MCP call failed for listPodsWithLabelInNamespace %s/%s=%s: %s. Falling back to Fabric8.",
                         namespace, labelKey, labelValue, e.getMessage());
            }
        }
        LOG.infof("Using Fabric8 to list pods in %s with label %s=%s", namespace, labelKey, labelValue);
        return listPodsWithLabelInNamespaceViaFabric8(namespace, labelKey, labelValue);
    }

    // ==================== Fabric8 Fallback Methods ====================

    private Pod getPodViaFabric8(String namespace, String podName) {
        LOG.debug("Using Fabric8 to fetch pod: " + namespace + "/" + podName);
        return kubernetesClient.pods().inNamespace(namespace).withName(podName).get();
    }

    private String getLogsViaFabric8(String namespace, String podName, int tailLines, boolean previous) {
        LOG.debug("Using Fabric8 to fetch logs: " + namespace + "/" + podName);
        try {
            if (previous) {
                return kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .terminated()
                    .tailingLines(tailLines)
                    .getLog();
            } else {
                return kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .tailingLines(tailLines)
                    .getLog();
            }
        } catch (Exception e) {
            LOG.error("Failed to fetch logs via Fabric8", e);
            return "Error fetching logs: " + e.getMessage();
        }
    }

    private List<Pod> listAllPodsViaFabric8() {
        LOG.debug("Using Fabric8 to list all pods across all namespaces");
        try {
            return kubernetesClient.pods().inAnyNamespace().list().getItems();
        } catch (Exception e) {
            LOG.error("Failed to list all pods via Fabric8", e);
            return List.of();
        }
    }

    private List<Pod> listPodsWithLabelViaFabric8(String labelKey, String labelValue) {
        LOG.debugf("Using Fabric8 to list pods with label %s=%s across all namespaces", labelKey, labelValue);
        try {
            return kubernetesClient.pods().inAnyNamespace()
                    .withLabel(labelKey, labelValue)
                    .list()
                    .getItems();
        } catch (Exception e) {
            LOG.errorf("Failed to list pods with label %s=%s via Fabric8", labelKey, labelValue, e);
            return List.of();
        }
    }

    private List<Pod> listPodsWithLabelInNamespaceViaFabric8(String namespace, String labelKey, String labelValue) {
        LOG.debugf("Using Fabric8 to list pods in %s with label %s=%s", namespace, labelKey, labelValue);
        try {
            return kubernetesClient.pods().inNamespace(namespace)
                    .withLabel(labelKey, labelValue)
                    .list()
                    .getItems();
        } catch (Exception e) {
            LOG.errorf("Failed to list pods in %s with label %s=%s via Fabric8", namespace, labelKey, labelValue, e);
            return List.of();
        }
    }

    private List<Event> getEventsViaFabric8(String namespace, String podName) {
        LOG.debug("Using Fabric8 to fetch events: " + namespace + "/" + podName);
        try {
            String podPrefix = derivePodPrefix(podName);
            return kubernetesClient.v1().events()
                .inNamespace(namespace)
                .list()
                .getItems()
                .stream()
                .filter(e -> e.getInvolvedObject() != null
                        && matchesPod(e.getInvolvedObject().getName(), podName, podPrefix))
                .collect(Collectors.toList());
        } catch (Exception e) {
            LOG.error("Failed to fetch events via Fabric8", e);
            return List.of();
        }
    }

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

    private String getStatusViaFabric8(String namespace, String podName) {
        LOG.debug("Using Fabric8 to fetch pod status: " + namespace + "/" + podName);
        try {
            Pod pod = kubernetesClient.pods().inNamespace(namespace).withName(podName).get();
            if (pod == null) {
                return "Pod not found";
            }
            if (pod.getStatus() == null) {
                return "Status not available";
            }

            StringBuilder statusInfo = new StringBuilder();
            statusInfo.append("Phase: ").append(pod.getStatus().getPhase()).append("\n");

            List<ContainerStatus> containerStatuses = pod.getStatus().getContainerStatuses();
            if (containerStatuses != null) {
                for (ContainerStatus cs : containerStatuses) {
                    statusInfo.append("Container: ").append(cs.getName()).append("\n");
                    statusInfo.append("  Ready: ").append(cs.getReady()).append("\n");
                    statusInfo.append("  Restart Count: ").append(cs.getRestartCount()).append("\n");

                    if (cs.getState() != null && cs.getState().getWaiting() != null) {
                        statusInfo.append("  Current State: Waiting (")
                            .append(cs.getState().getWaiting().getReason()).append(")\n");
                        statusInfo.append("  Message: ")
                            .append(cs.getState().getWaiting().getMessage()).append("\n");
                    }

                    if (cs.getLastState() != null && cs.getLastState().getTerminated() != null) {
                        statusInfo.append("  Last State: Terminated (")
                            .append(cs.getLastState().getTerminated().getReason()).append(")\n");
                        statusInfo.append("  Exit Code: ")
                            .append(cs.getLastState().getTerminated().getExitCode()).append("\n");
                        statusInfo.append("  Finished At: ")
                            .append(cs.getLastState().getTerminated().getFinishedAt()).append("\n");
                    }
                }
            }
            return statusInfo.toString();
        } catch (Exception e) {
            LOG.error("Failed to fetch pod status via Fabric8", e);
            return "Error fetching pod status: " + e.getMessage();
        }
    }

    // ==================== Pod List Parsing ====================

    /**
     * Parse a list of lightweight {@link Pod} objects from the plain-text table
     * returned by the MCP {@code pods_list} tool.
     *
     * <p>The MCP server (kubernetes-mcp-server v0.0.57+) returns a multi-space-separated
     * table with a header row.  Columns are separated by two or more spaces.  The last
     * column is LABELS, which contains comma-separated {@code key=value} pairs.
     *
     * <p>Example row:
     * <pre>
     * default   v1   Pod   my-pod   1/1   Running   0   5d   10.0.0.1   node1   <none>   <none>   app=my-app,env=prod
     * </pre>
     *
     * <p>Columns extracted: NAMESPACE (0), NAME (3), STATUS (5), RESTARTS (6), LABELS (last).
     *
     * @param podsText plain-text table returned by the MCP {@code pods_list} tool
     * @return parsed list of pods, never {@code null}
     * @throws IllegalStateException if the response is empty or the header row is missing
     */
    private List<Pod> parsePodsFromMcpText(String podsText) {
        if (podsText == null || podsText.isBlank()) {
            throw new IllegalStateException("MCP pods_list returned an empty response");
        }

        LOG.debugf("MCP pods_list raw response (first 500 chars): %.500s", podsText);

        String[] lines = podsText.split("\n");
        if (lines.length < 2) {
            throw new IllegalStateException(
                    "MCP pods_list response has fewer than 2 lines (no data rows): " + podsText);
        }

        // Validate header contains expected columns
        String header = lines[0];
        if (!header.contains("NAMESPACE") || !header.contains("NAME")) {
            throw new IllegalStateException(
                    "MCP pods_list response header missing expected columns. Header: " + header);
        }

        // Determine column indices from the header by splitting on 2+ spaces
        // Header: NAMESPACE  APIVERSION  KIND  NAME  READY  STATUS  RESTARTS  AGE  IP  NODE  NOMINATED NODE  READINESS GATES  LABELS
        String[] headerCols = header.split("  +");
        int nsColIdx       = -1;
        int nameColIdx     = -1;
        int statusColIdx   = -1;
        int restartsColIdx = -1;
        int labelsColIdx   = -1;
        for (int c = 0; c < headerCols.length; c++) {
            String col = headerCols[c].trim();
            switch (col) {
                case "NAMESPACE" -> nsColIdx = c;
                case "NAME"      -> nameColIdx = c;
                case "STATUS"    -> statusColIdx = c;
                case "RESTARTS"  -> restartsColIdx = c;
                case "LABELS"    -> labelsColIdx = c;
            }
        }

        if (nsColIdx < 0 || nameColIdx < 0) {
            throw new IllegalStateException(
                    "Could not locate NAMESPACE or NAME column in MCP pods_list header: " + header);
        }

        List<Pod> pods = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) continue;

            // Split data row on 2+ spaces — same delimiter as header
            String[] cols = line.split("  +");

            String namespace = getCol(cols, nsColIdx);
            String name      = getCol(cols, nameColIdx);
            String status    = statusColIdx >= 0 ? getCol(cols, statusColIdx) : "Unknown";
            String restarts  = restartsColIdx >= 0 ? getCol(cols, restartsColIdx) : "0";
            String labelsRaw = labelsColIdx >= 0 ? getCol(cols, labelsColIdx) : "";

            if (namespace.isEmpty() || name.isEmpty()) continue;

            // Build metadata with namespace, name, and parsed labels
            io.fabric8.kubernetes.api.model.ObjectMeta meta = new io.fabric8.kubernetes.api.model.ObjectMeta();
            meta.setNamespace(namespace);
            meta.setName(name);

            // Parse labels: "key1=val1,key2=val2" → Map
            if (!labelsRaw.isEmpty() && !labelsRaw.equals("<none>")) {
                Map<String, String> labels = new HashMap<>();
                for (String pair : labelsRaw.split(",")) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2) {
                        labels.put(kv[0].trim(), kv[1].trim());
                    }
                }
                meta.setLabels(labels);
            }

            io.fabric8.kubernetes.api.model.PodStatus podStatus = new io.fabric8.kubernetes.api.model.PodStatus();
            podStatus.setPhase(status.isEmpty() ? "Unknown" : status);

            // Parse restart count — value may be "0", "3", or "3 (2 last 5d)"
            int restartCount = 0;
            try {
                restartCount = Integer.parseInt(restarts.trim().split("\\s+")[0]);
            } catch (NumberFormatException ignored) { /* leave 0 */ }

            io.fabric8.kubernetes.api.model.ContainerStatus cs =
                    new io.fabric8.kubernetes.api.model.ContainerStatus();
            cs.setRestartCount(restartCount);
            podStatus.setContainerStatuses(List.of(cs));

            Pod pod = new Pod();
            pod.setMetadata(meta);
            pod.setStatus(podStatus);
            pods.add(pod);
        }

        LOG.infof("Parsed %d pods from MCP plain-text table", pods.size());
        return pods;
    }

    /**
     * Safely retrieve a column value from a split row array.
     *
     * @param cols  the split columns
     * @param index the column index
     * @return trimmed value, or empty string if index is out of bounds
     */
    private String getCol(String[] cols, int index) {
        if (index < 0 || index >= cols.length) return "";
        return cols[index].trim();
    }

    // ==================== Helper Methods ====================

    /**
     * Parse events from the custom text format returned by the MCP events_list tool.
     * The format is a YAML list with fields: InvolvedObject.Name, Message, Reason, Timestamp, Type.
     *
     * <p>Applies the same three-tier matching used by the Fabric8 fallback:
     * <ol>
     *   <li>Exact match — event is for the pod itself</li>
     *   <li>Pod name starts with object name — object is the owning ReplicaSet</li>
     *   <li>Object name starts with pod prefix — sibling pod / Deployment event</li>
     * </ol>
     *
     * Example entry:
     * <pre>
     * - InvolvedObject:
     *     Kind: Pod
     *     Name: my-pod
     *   Message: Pulled image
     *   Reason: Pulled
     *   Timestamp: 2026-02-27 13:30:54 +0000 UTC
     *   Type: Normal
     * </pre>
     */
    private List<Event> parseEventsFromMcpText(String eventsText, String podName) {
        List<Event> result = new ArrayList<>();
        if (eventsText == null || eventsText.isBlank()) {
            return result;
        }

        String podPrefix = derivePodPrefix(podName);

        // Split on list item boundaries (lines starting with "- ")
        // Each block starts with "- InvolvedObject:" or similar
        String[] lines = eventsText.split("\n");
        
        String currentName = null;
        String currentMessage = null;
        String currentReason = null;
        String currentType = null;
        String currentTimestamp = null;
        boolean inInvolvedObject = false;

        for (String line : lines) {
            String trimmed = line.trim();
            
            // New list item
            if (trimmed.startsWith("- InvolvedObject:")) {
                // Save previous entry if it matches (same three-tier logic as Fabric8 fallback)
                if (matchesPod(currentName, podName, podPrefix)) {
                    result.add(buildEvent(currentName, currentMessage, currentReason, currentType, currentTimestamp));
                }
                currentName = null;
                currentMessage = null;
                currentReason = null;
                currentType = null;
                currentTimestamp = null;
                inInvolvedObject = true;
                continue;
            }
            
            if (inInvolvedObject && trimmed.startsWith("Name:")) {
                currentName = trimmed.substring("Name:".length()).trim();
                inInvolvedObject = false;
                continue;
            }
            
            if (trimmed.startsWith("Message:")) {
                currentMessage = trimmed.substring("Message:".length()).trim();
                // Remove surrounding quotes if present
                if (currentMessage.startsWith("'") && currentMessage.endsWith("'")) {
                    currentMessage = currentMessage.substring(1, currentMessage.length() - 1);
                }
                continue;
            }
            
            if (trimmed.startsWith("Reason:")) {
                currentReason = trimmed.substring("Reason:".length()).trim();
                continue;
            }
            
            if (trimmed.startsWith("Type:")) {
                currentType = trimmed.substring("Type:".length()).trim();
                continue;
            }
            
            if (trimmed.startsWith("Timestamp:")) {
                currentTimestamp = trimmed.substring("Timestamp:".length()).trim();
                continue;
            }
        }
        
        // Add the last entry
        if (matchesPod(currentName, podName, podPrefix)) {
            result.add(buildEvent(currentName, currentMessage, currentReason, currentType, currentTimestamp));
        }
        
        return result;
    }

    /**
     * Returns {@code true} if {@code objName} should be included when collecting events for {@code podName}.
     * Applies the same three-tier matching used by the Fabric8 fallback:
     * <ol>
     *   <li>Exact match — event is for the pod itself</li>
     *   <li>{@code podName} starts with {@code objName + "-"} — object is the owning ReplicaSet</li>
     *   <li>{@code objName} starts with {@code podPrefix} — sibling pod or Deployment event</li>
     * </ol>
     */
    private boolean matchesPod(String objName, String podName, String podPrefix) {
        if (objName == null) return false;
        if (podName.equals(objName)) return true;
        if (podName.startsWith(objName + "-")) return true;
        if (!podPrefix.isEmpty() && objName.startsWith(podPrefix)) return true;
        return false;
    }

    private Event buildEvent(String podName, String message, String reason, String type, String timestamp) {
        Event event = new Event();
        io.fabric8.kubernetes.api.model.ObjectReference ref = new io.fabric8.kubernetes.api.model.ObjectReference();
        ref.setName(podName);
        ref.setKind("Pod");
        event.setInvolvedObject(ref);
        event.setMessage(message);
        event.setReason(reason);
        event.setType(type);
        if (timestamp != null) {
            event.setLastTimestamp(timestamp);
        }
        return event;
    }

    private String formatPodStatusFromJson(JsonNode statusJson) {
        StringBuilder statusInfo = new StringBuilder();

        if (statusJson.has("status")) {
            JsonNode status = statusJson.get("status");
            JsonNode phaseNode = status.path("phase");
            if (!phaseNode.isMissingNode()) {
                statusInfo.append("Phase: ")
                          .append(phaseNode.asText("<unknown>"))
                          .append("\n");
            }

            if (status.has("containerStatuses")) {
                for (JsonNode cs : status.get("containerStatuses")) {
                    statusInfo.append("Container: ")
                              .append(cs.path("name").asText("<unknown>"))
                              .append("\n");
                    statusInfo.append("  Ready: ")
                              .append(cs.path("ready").asBoolean(false))
                              .append("\n");
                    statusInfo.append("  Restart Count: ")
                              .append(cs.path("restartCount").asInt(0))
                              .append("\n");

                    JsonNode state = cs.path("state");
                    JsonNode waiting = state.path("waiting");
                    if (!waiting.isMissingNode()) {
                        statusInfo.append("  Current State: Waiting (")
                                  .append(waiting.path("reason").asText("<unknown>"))
                                  .append(")\n");
                        JsonNode messageNode = waiting.path("message");
                        if (!messageNode.isMissingNode()) {
                            statusInfo.append("  Message: ")
                                      .append(messageNode.asText())
                                      .append("\n");
                        }
                    }

                    JsonNode terminated = cs.path("lastState").path("terminated");
                    if (!terminated.isMissingNode()) {
                        statusInfo.append("  Last State: Terminated (")
                                  .append(terminated.path("reason").asText("<unknown>"))
                                  .append(")\n");
                        statusInfo.append("  Exit Code: ")
                                  .append(terminated.path("exitCode").asInt(0))
                                  .append("\n");
                        JsonNode finishedAtNode = terminated.path("finishedAt");
                        if (!finishedAtNode.isMissingNode()) {
                            statusInfo.append("  Finished At: ")
                                      .append(finishedAtNode.asText())
                                      .append("\n");
                        }
                    }
                }
            }
        }
        
        return statusInfo.toString();
    }
}
