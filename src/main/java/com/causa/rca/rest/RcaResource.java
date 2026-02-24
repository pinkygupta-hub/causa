package com.causa.rca.rest;

import com.causa.rca.model.AlertWebhookRequest;
import com.causa.rca.model.RcaReport;
import com.causa.rca.service.RcaOrchestrator;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    /**
     * Triggers a comprehensive Root Cause Analysis for a specific pod.
     * <p>
     * Initiates the complete RCA pipeline including:
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
     * @return an {@link RcaReport} containing the complete analysis results including
     *         issue description, evidence, logs, proposed solution, and confidence score
     * @throws BadRequestException if the pod parameter is null or empty
     */
    @GET
    @Path("/analyze")
    public RcaReport analyze(
            @QueryParam("namespace") @DefaultValue("default") String namespace,
            @QueryParam("pod") String pod) {
        if (pod == null || pod.isEmpty()) {
            throw new BadRequestException("Pod name is required");
        }
        return orchestrator.runAnalysis(namespace, pod);
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
        LOG.info("Received webhook alert. Status: " + alertRequest.getStatus());
        
        if (alertRequest.getAlerts() == null || alertRequest.getAlerts().isEmpty()) {
            LOG.warn("Webhook received with no alerts");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "No alerts in webhook payload"))
                    .build();
        }

        List<Map<String, Object>> results = new ArrayList<>();
        int processedCount = 0;
        int skippedCount = 0;
        int errorCount = 0;

        for (AlertWebhookRequest.Alert alert : alertRequest.getAlerts()) {
            String namespace = alert.getNamespace();
            String podName = alert.getPodName();
            String alertName = alert.getAlertName();
            String status = alert.getStatus();

            LOG.info("Processing alert: " + alertName + " for pod: " + namespace + "/" + podName + " (status: " + status + ")");

            // Skip resolved alerts or alerts without required information
            if (!"firing".equalsIgnoreCase(status)) {
                LOG.info("Skipping non-firing alert: " + alertName);
                skippedCount++;
                continue;
            }

            if (namespace == null || namespace.isEmpty() || podName == null || podName.isEmpty()) {
                LOG.warn("Alert missing required labels (namespace/pod): " + alertName);
                results.add(Map.of(
                    "alert", alertName != null ? alertName : "unknown",
                    "status", "skipped",
                    "reason", "Missing namespace or pod labels"
                ));
                skippedCount++;
                continue;
            }

            try {
                LOG.info("Triggering RCA analysis for: " + namespace + "/" + podName);
                RcaReport report = orchestrator.runAnalysis(namespace, podName);
                
                Map<String, Object> result = new HashMap<>();
                result.put("alert", alertName);
                result.put("namespace", namespace);
                result.put("pod", podName);
                result.put("status", "analyzed");
                result.put("issue", report.issue);
                result.put("confidence", report.validationConfidence);
                results.add(result);
                
                processedCount++;
                LOG.info("RCA analysis completed for: " + namespace + "/" + podName);
            } catch (Exception e) {
                LOG.error("Error processing alert for pod: " + namespace + "/" + podName, e);
                results.add(Map.of(
                    "alert", alertName,
                    "namespace", namespace,
                    "pod", podName,
                    "status", "error",
                    "error", e.getMessage() != null ? e.getMessage() : "Unknown error"
                ));
                errorCount++;
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Webhook processed");
        response.put("totalAlerts", alertRequest.getAlerts().size());
        response.put("processed", processedCount);
        response.put("skipped", skippedCount);
        response.put("errors", errorCount);
        response.put("results", results);

        LOG.info("Webhook processing complete. Processed: " + processedCount + ", Skipped: " + skippedCount + ", Errors: " + errorCount);

        return Response.ok(response).build();
    }
}
