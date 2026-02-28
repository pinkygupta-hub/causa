package com.causa.rca.clients.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP service using the quarkus-langchain4j-mcp extension.
 *
 * <p>The extension manages the full {@link McpClient} lifecycle for the named client
 * {@code "k8s"}, driven entirely by {@code application.properties}:
 * <pre>
 *   quarkus.langchain4j.mcp.k8s.transport-type=streamable-http
 *   quarkus.langchain4j.mcp.k8s.url=http://kubernetes-mcp-server:3000/mcp
 *   quarkus.langchain4j.mcp.k8s.tool-execution-timeout=60s
 * </pre>
 *
 * <p>This service wraps the injected client and exposes typed helper methods for
 * the Kubernetes operations used by the rest of the application:
 * <ul>
 *   <li>Pod info ({@code pods_get})</li>
 *   <li>Pod logs ({@code pods_log})</li>
 *   <li>Namespace events ({@code events_list})</li>
 * </ul>
 *
 * <p>The extension always injects a non-null {@link McpClient} bean regardless of
 * whether the MCP server is reachable at startup — the MCP {@code initialize}
 * handshake is performed lazily on the first tool call.  {@link #isReady()} therefore
 * always returns {@code true}; resilience is provided by the {@code try/catch} blocks
 * in {@link com.causa.rca.clients.KubernetesMcpClient}, which fall back to the direct
 * Fabric8 path whenever a tool call throws an exception (e.g. connection refused,
 * timeout, or protocol error).
 */
@ApplicationScoped
public class Langchain4jMcpService {

    private static final Logger LOG = Logger.getLogger(Langchain4jMcpService.class);

    /**
     * How long (ms) to wait before retrying MCP after a connectivity failure.
     * Defaults to 60 seconds; can be overridden in tests.
     */
    static long CIRCUIT_OPEN_DURATION_MS = 60_000L;

    /** Set to {@code true} while the circuit is open (MCP is considered unavailable). */
    private final AtomicBoolean circuitOpen = new AtomicBoolean(false);

    /** Timestamp (ms) at which the circuit was opened; used to compute the cooldown. */
    private final AtomicLong circuitOpenedAt = new AtomicLong(0L);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * The MCP client for the "k8s" server, managed by the quarkus-langchain4j-mcp
     * extension.  The extension performs the MCP {@code initialize} handshake lazily
     * on the first tool call, handles reconnection, and closes the client on
     * application shutdown.
     *
     * <p>This field is <em>never</em> {@code null}: the extension always produces a
     * non-null {@link McpClient} bean at startup regardless of server reachability.
     */
    @Inject
    @McpClientName("k8s")
    McpClient mcpClient;

    /**
     * Returns {@code true} when MCP calls should be attempted.
     *
     * <p>Implements a simple time-based circuit breaker:
     * <ul>
     *   <li>Returns {@code true} normally (circuit closed).</li>
     *   <li>After {@link #recordFailure()} is called the circuit opens and this
     *       method returns {@code false} for {@link #CIRCUIT_OPEN_DURATION_MS} ms.</li>
     *   <li>After the cooldown expires the circuit closes again and the next call
     *       is allowed through as a probe.  If it succeeds, {@link #recordSuccess()}
     *       keeps the circuit closed; if it fails, {@link #recordFailure()} re-opens it.</li>
     * </ul>
     *
     * @return {@code false} while the circuit is open (MCP unreachable cooldown active),
     *         {@code true} otherwise
     */
    public boolean isReady() {
        if (!circuitOpen.get()) {
            return true;
        }
        // Check whether the cooldown has expired
        long elapsed = System.currentTimeMillis() - circuitOpenedAt.get();
        if (elapsed >= CIRCUIT_OPEN_DURATION_MS) {
            LOG.infof("MCP circuit-breaker cooldown expired (%d ms). Probing MCP server.", elapsed);
            circuitOpen.set(false);
            return true;
        }
        LOG.debugf("MCP circuit-breaker open — skipping MCP attempt (%d ms remaining in cooldown)",
                   CIRCUIT_OPEN_DURATION_MS - elapsed);
        return false;
    }

    /**
     * Records a successful MCP tool call — closes the circuit breaker.
     */
    void recordSuccess() {
        if (circuitOpen.compareAndSet(true, false)) {
            LOG.info("MCP circuit-breaker closed — MCP server is reachable again.");
        }
    }

    /**
     * Records a failed MCP tool call — opens the circuit breaker for the cooldown period.
     */
    void recordFailure() {
        circuitOpenedAt.set(System.currentTimeMillis());
        if (circuitOpen.compareAndSet(false, true)) {
            LOG.warnf("MCP circuit-breaker opened — MCP calls suppressed for %d s.",
                      CIRCUIT_OPEN_DURATION_MS / 1000);
        }
    }

    /**
     * Get pod information via MCP ({@code pods_get} tool).
     *
     * @param namespace the Kubernetes namespace
     * @param name      the pod name
     * @return pod YAML as a string
     * @throws Exception if the MCP call fails
     */
    public String getPod(String namespace, String name) throws Exception {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("namespace", namespace);
        arguments.put("name", name);
        return invokeTool("pods_get", arguments);
    }

    /**
     * Get pod logs via MCP ({@code pods_log} tool).
     *
     * @param namespace the Kubernetes namespace
     * @param name      the pod name
     * @param tail      number of lines to tail (optional)
     * @param previous  whether to get previous container logs (optional)
     * @return pod logs as text
     * @throws Exception if the MCP call fails
     */
    public String getPodLogs(String namespace, String name, Integer tail, Boolean previous) throws Exception {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("namespace", namespace);
        arguments.put("name", name);
        if (tail != null) {
            arguments.put("tail", tail);
        }
        if (previous != null) {
            arguments.put("previous", previous);
        }
        return invokeTool("pods_log", arguments);
    }

    /**
     * List pods in a namespace via MCP ({@code pods_list} tool).
     *
     * <p>Returns a YAML list of pods in the given namespace.  Pass {@code null} for
     * {@code namespace} to list pods across all namespaces (the MCP server omits the
     * namespace filter when the argument is absent).
     *
     * @param namespace the Kubernetes namespace, or {@code null} for all namespaces
     * @return pod list as a YAML string
     * @throws Exception if the MCP call fails
     */
    public String listPods(String namespace) throws Exception {
        Map<String, Object> arguments = new HashMap<>();
        if (namespace != null) {
            arguments.put("namespace", namespace);
        }
        return invokeTool("pods_list", arguments);
    }

    /**
     * List Kubernetes events via MCP ({@code events_list} tool).
     *
     * <p>Note: {@code events_list} only supports namespace-level filtering; pod-level
     * filtering is performed client-side in
     * {@link com.causa.rca.clients.KubernetesMcpClient#getPodEvents}.
     * The response is a custom YAML-like text format, not a Kubernetes EventList JSON.
     *
     * @param namespace the Kubernetes namespace
     * @return events as a custom text/YAML format string
     * @throws Exception if the MCP call fails
     */
    public String listEvents(String namespace) throws Exception {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("namespace", namespace);
        return invokeTool("events_list", arguments);
    }

    /**
     * Get pod status via MCP.
     *
     * <p>Delegates to {@link #getPod} — {@code pods_get} returns the full pod YAML
     * which includes the {@code status} section.  The caller is responsible for
     * parsing the YAML to extract status information.
     *
     * @param namespace the Kubernetes namespace
     * @param name      the pod name
     * @return full pod YAML as a string (includes status section)
     * @throws Exception if the MCP call fails
     */
    public String getPodStatus(String namespace, String name) throws Exception {
        return getPod(namespace, name);
    }

    // ==================== Internal helpers ====================

    /**
     * Invoke an MCP tool using the extension-managed client.
     *
     * @param toolName  the MCP tool name
     * @param arguments the tool arguments
     * @return the tool result text
     * @throws Exception if the client is not ready or the call fails
     */
    private String invokeTool(String toolName, Map<String, Object> arguments) throws Exception {
        if (!isReady()) {
            throw new IllegalStateException(
                    "MCP circuit-breaker is open — MCP server is unreachable. " +
                    "Falling back to Fabric8. Check QUARKUS_LANGCHAIN4J_MCP__K8S__URL and " +
                    "ensure the route uses https:// with insecureEdgeTerminationPolicy: Allow.");
        }

        LOG.debugf("Invoking MCP tool '%s' with arguments: %s", toolName, arguments);

        String argumentsJson = objectMapper.writeValueAsString(arguments);

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name(toolName)
                .arguments(argumentsJson)
                .build();

        try {
            ToolExecutionResult toolResult = mcpClient.executeTool(request);
            String result = toolResult.resultText();
            recordSuccess();
            LOG.debugf("MCP tool '%s' completed successfully", toolName);
            return result;
        } catch (Exception e) {
            recordFailure();
            throw e;
        }
    }
}
