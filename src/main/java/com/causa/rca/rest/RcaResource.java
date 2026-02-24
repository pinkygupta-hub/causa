package com.causa.rca.rest;

import com.causa.rca.model.AlertWebhookRequest;
import com.causa.rca.model.RcaAnalysisSession;
import com.causa.rca.model.RcaReport;
import com.causa.rca.service.RcaOrchestrator;
import com.causa.rca.service.WebhookManagerService;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * REST API resource for Root Cause Analysis operations.
 * <p>
 * This resource provides HTTP endpoints for triggering RCA analysis on Kubernetes pods.
 * It serves as the entry point for external clients to request diagnostic analysis
 * of pod issues and receive structured RCA reports.
 * </p>
 * <p>
 * All endpoints produce and consume JSON format for easy integration with various clients.
 * </p>
 *
 * @see RcaOrchestrator
 * @see RcaReport
 */
@Path("/rca")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RcaResource {

    private static final Logger LOG = Logger.getLogger(RcaResource.class);

    @Inject
    RcaOrchestrator orchestrator;

    @Inject
    WebhookManagerService webhookManager;

    /**
     * Starts an asynchronous Root Cause Analysis for a specific pod.
     * <p>
     * Initiates the RCA pipeline asynchronously and returns immediately with session information.
     * The analysis runs in the background through the following stages:
     * <ol>
     *   <li>Data collection (metrics, logs, events, JFR data)</li>
     *   <li>Anomaly detection using AI</li>
     *   <li>Root cause analysis with detailed reasoning</li>
     *   <li>Validation and formatting of results</li>
     * </ol>
     * </p>
     * <p>
     * Example usage:
     * <pre>
     * GET /rca/analyze?namespace=production&pod=my-app-pod-12345
     * </pre>
     * </p>
     *
     * @param namespace the Kubernetes namespace where the pod is located (defaults to "default")
     * @param pod the name of the pod to analyze (required)
     * @return an {@link RcaAnalysisSession} with status IN_PROGRESS and session details
     * @throws BadRequestException if the pod parameter is null or empty
     */
    @GET
    @Path("/analyze")
    public RcaAnalysisSession analyze(
            @QueryParam("namespace") @DefaultValue("default") String namespace,
            @QueryParam("pod") String pod) {
        if (pod == null || pod.isEmpty()) {
            throw new BadRequestException("Pod name is required");
        }
        return orchestrator.startAnalysis(namespace, pod);
    }

    /**
     * Webhook endpoint for receiving Prometheus Alertmanager alerts.
     * <p>
     * This endpoint is designed to be called by Prometheus Alertmanager when alerts fire.
     * It processes incoming alerts and triggers RCA analysis for the affected pods.
     * </p>
     * <p>
     * The webhook expects alerts to contain labels with:
     * <ul>
     *   <li><b>namespace</b>: The Kubernetes namespace of the affected pod</li>
     *   <li><b>pod</b> or <b>pod_name</b>: The name of the affected pod</li>
     * </ul>
     * </p>
     * <p>
     * Example Alertmanager webhook configuration:
     * <pre>
     * receivers:
     * - name: 'rca-webhook'
     *   webhook_configs:
     *   - url: 'http://causa-service:8080/rca/webhook'
     *     send_resolved: false
     * </pre>
     * </p>
     * <p>
     * The endpoint processes all firing alerts in the webhook payload and returns
     * a summary of the analysis results for each pod.
     * </p>
     *
     * @param alertRequest the webhook payload from Prometheus Alertmanager
     * @return a Response containing a summary of processed alerts and their RCA results
     */
    @POST
    @Path("/webhook")
    public Response handleWebhook(AlertWebhookRequest alertRequest) {
        LOG.info("Received webhook request");
        Map<String, Object> result = webhookManager.processWebhook(alertRequest);
        return Response.ok(result).build();
    }
}
