package com.causa.rca.service;

import com.causa.rca.model.AlertWebhookRequest;
import com.causa.rca.model.RcaReport;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for processing webhook requests from alerting systems.
 * <p>
 * This service handles incoming webhook payloads from Prometheus Alertmanager and
 * other monitoring systems, extracting relevant information and triggering RCA
 * analysis for affected pods.
 * </p>
 * <p>
 * The service provides:
 * <ul>
 *   <li><b>Alert Processing:</b> Parses and validates webhook payloads</li>
 *   <li><b>Filtering:</b> Processes only firing alerts with required information</li>
 *   <li><b>Analysis Coordination:</b> Triggers RCA for each valid alert</li>
 *   <li><b>Result Aggregation:</b> Collects and summarizes analysis results</li>
 *   <li><b>Error Handling:</b> Continues processing even if individual alerts fail</li>
 * </ul>
 * </p>
 * <p>
 * This modular design allows for:
 * <ul>
 *   <li>Easy testing of webhook processing logic</li>
 *   <li>Support for multiple alerting systems</li>
 *   <li>Clear separation from REST layer</li>
 * </ul>
 * </p>
 *
 * @see AlertWebhookRequest
 * @see ScannerService
 * @see RcaOrchestrator
 */
@ApplicationScoped
public class WebhookManagerService {

    private static final Logger LOG = Logger.getLogger(WebhookManagerService.class);

    @Inject
    RcaOrchestrator rcaOrchestrator;

    /**
     * Processes a webhook request containing one or more alerts.
     * <p>
     * This method:
     * <ol>
     *   <li>Validates the webhook payload</li>
     *   <li>Filters alerts (only processes firing alerts with required labels)</li>
     *   <li>Triggers RCA analysis for each valid alert</li>
     *   <li>Aggregates results and error information</li>
     *   <li>Returns a comprehensive summary</li>
     * </ol>
     * </p>
     * <p>
     * The method is resilient - if analysis fails for one alert, it continues
     * processing the remaining alerts.
     * </p>
     *
     * @param alertRequest the webhook payload from the alerting system
     * @return a Map containing processing summary with keys:
     *         <ul>
     *           <li>{@code message}: Overall status message</li>
     *           <li>{@code totalAlerts}: Total number of alerts in the payload</li>
     *           <li>{@code processed}: Number of alerts successfully analyzed</li>
     *           <li>{@code skipped}: Number of alerts skipped (non-firing or missing labels)</li>
     *           <li>{@code errors}: Number of alerts that failed during analysis</li>
     *           <li>{@code results}: List of individual alert processing results</li>
     *         </ul>
     */
    public Map<String, Object> processWebhook(AlertWebhookRequest alertRequest) {
        LOG.info("Processing webhook. Status: " + alertRequest.getStatus());

        List<Map<String, Object>> results = new ArrayList<>();
        int processedCount = 0;
        int skippedCount = 0;
        int errorCount = 0;

        if (alertRequest.getAlerts() == null || alertRequest.getAlerts().isEmpty()) {
            LOG.warn("Webhook received with no alerts");
            return createErrorResponse("No alerts in webhook payload");
        }

        for (AlertWebhookRequest.Alert alert : alertRequest.getAlerts()) {
            String namespace = alert.getNamespace();
            String podName = alert.getPodName();
            String alertName = alert.getAlertName();
            String status = alert.getStatus();

            LOG.info("Processing alert: " + alertName + " for pod: " + namespace + "/" + podName + 
                    " (status: " + status + ")");

            // Skip non-firing alerts
            if (!"firing".equalsIgnoreCase(status)) {
                LOG.info("Skipping non-firing alert: " + alertName);
                results.add(createSkippedResult(alertName, "Alert is not firing"));
                skippedCount++;
                continue;
            }

            // Skip alerts without required information
            if (namespace == null || namespace.isEmpty() || podName == null || podName.isEmpty()) {
                LOG.warn("Alert missing required labels (namespace/pod): " + alertName);
                results.add(createSkippedResult(
                    alertName != null ? alertName : "unknown",
                    "Missing namespace or pod labels"
                ));
                skippedCount++;
                continue;
            }

            // Process the alert
            try {
                LOG.info("Triggering RCA analysis for: " + namespace + "/" + podName);
                RcaReport report = rcaOrchestrator.runAnalysis(namespace, podName);
                
                results.add(createSuccessResult(alertName, namespace, podName, report));
                processedCount++;
                
                LOG.info("RCA analysis completed for: " + namespace + "/" + podName);
            } catch (Exception e) {
                LOG.error("Error processing alert for pod: " + namespace + "/" + podName, e);
                results.add(createErrorResult(alertName, namespace, podName, e));
                errorCount++;
            }
        }

        LOG.info("Webhook processing complete. Processed: " + processedCount + 
                ", Skipped: " + skippedCount + ", Errors: " + errorCount);

        return createSummaryResponse(
            alertRequest.getAlerts().size(),
            processedCount,
            skippedCount,
            errorCount,
            results
        );
    }

    /**
     * Processes a single alert directly.
     * <p>
     * This method provides a simplified interface for processing individual alerts
     * without the full webhook payload structure. Useful for testing or direct
     * alert processing.
     * </p>
     *
     * @param namespace the Kubernetes namespace of the affected pod
     * @param podName the name of the affected pod
     * @param alertName the name of the alert (for logging)
     * @return a Map containing the processing result
     */
    public Map<String, Object> processAlert(String namespace, String podName, String alertName) {
        LOG.info("Processing single alert: " + alertName + " for pod: " + namespace + "/" + podName);

        if (namespace == null || namespace.isEmpty() || podName == null || podName.isEmpty()) {
            return createErrorResponse("Missing namespace or pod name");
        }

        try {
            RcaReport report = rcaOrchestrator.runAnalysis(namespace, podName);
            return createSuccessResult(alertName, namespace, podName, report);
        } catch (Exception e) {
            LOG.error("Error processing alert for pod: " + namespace + "/" + podName, e);
            return createErrorResult(alertName, namespace, podName, e);
        }
    }

    /**
     * Creates a success result map for an analyzed alert.
     */
    private Map<String, Object> createSuccessResult(String alertName, String namespace, 
                                                     String podName, RcaReport report) {
        Map<String, Object> result = new HashMap<>();
        result.put("alert", alertName);
        result.put("namespace", namespace);
        result.put("pod", podName);
        result.put("status", "analyzed");
        result.put("issue", report.issue);
        result.put("confidence", report.validationConfidence);
        return result;
    }

    /**
     * Creates a skipped result map for an alert that was not processed.
     */
    private Map<String, Object> createSkippedResult(String alertName, String reason) {
        Map<String, Object> result = new HashMap<>();
        result.put("alert", alertName);
        result.put("status", "skipped");
        result.put("reason", reason);
        return result;
    }

    /**
     * Creates an error result map for an alert that failed during processing.
     */
    private Map<String, Object> createErrorResult(String alertName, String namespace, 
                                                   String podName, Exception e) {
        Map<String, Object> result = new HashMap<>();
        result.put("alert", alertName);
        result.put("namespace", namespace);
        result.put("pod", podName);
        result.put("status", "error");
        result.put("error", e.getMessage() != null ? e.getMessage() : "Unknown error");
        return result;
    }

    /**
     * Creates a summary response map for the entire webhook processing.
     */
    private Map<String, Object> createSummaryResponse(int totalAlerts, int processed, 
                                                       int skipped, int errors, 
                                                       List<Map<String, Object>> results) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Webhook processed");
        response.put("totalAlerts", totalAlerts);
        response.put("processed", processed);
        response.put("skipped", skipped);
        response.put("errors", errors);
        response.put("results", results);
        return response;
    }

    /**
     * Creates an error response map for invalid webhook requests.
     */
    private Map<String, Object> createErrorResponse(String errorMessage) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", errorMessage);
        response.put("totalAlerts", 0);
        response.put("processed", 0);
        response.put("skipped", 0);
        response.put("errors", 0);
        response.put("results", new ArrayList<>());
        return response;
    }
}

// Made with Bob
