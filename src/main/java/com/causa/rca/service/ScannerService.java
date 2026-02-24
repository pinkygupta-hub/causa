package com.causa.rca.service;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Service responsible for scanning Kubernetes workloads and triggering RCA analysis.
 * <p>
 * This service provides the core scanning logic for identifying pods that require
 * analysis. It queries the Kubernetes API for pods matching specific label selectors
 * and coordinates with the RCA orchestrator to perform analysis on each matching pod.
 * </p>
 * <p>
 * The scanner is designed to be:
 * <ul>
 *   <li><b>Resilient:</b> Continues processing remaining pods if one fails</li>
 *   <li><b>Flexible:</b> Works with any workload type (Deployments, StatefulSets, DaemonSets)</li>
 *   <li><b>Configurable:</b> Uses label selectors to target specific pods</li>
 *   <li><b>Modular:</b> Can be invoked by scheduler or other services</li>
 * </ul>
 * </p>
 * <p>
 * Example label configuration: {@code rca.label=kruize/rca=enabled}
 * </p>
 *
 * @see LifecycleService
 * @see RcaOrchestrator
 */
@ApplicationScoped
public class ScannerService {

    private static final Logger LOG = Logger.getLogger(ScannerService.class);

    @Inject
    KubernetesClient kubernetesClient;

    @Inject
    RcaOrchestrator rcaOrchestrator;

    /**
     * Label selector for identifying pods to analyze.
     * <p>
     * Format: "key=value" (e.g., "kruize/rca=enabled")
     * </p>
     */
    @ConfigProperty(name = "rca.label")
    String rcaLabel;

    /**
     * Scans all pods with the configured label and triggers RCA analysis.
     * <p>
     * This method performs the following steps:
     * <ol>
     *   <li>Parses the configured label selector</li>
     *   <li>Queries Kubernetes API for all pods across namespaces with that label</li>
     *   <li>Triggers RCA analysis for each matching pod</li>
     *   <li>Logs results and any errors encountered</li>
     * </ol>
     * </p>
     * <p>
     * The method is designed to be resilient - if analysis fails for one pod,
     * it continues processing the remaining pods. This ensures that a single
     * failing pod doesn't prevent analysis of other pods.
     * </p>
     *
     * @return the number of pods successfully analyzed
     */
    public int scanWorkloads() {
        LOG.info("Starting workload scan for RCA...");

        String[] labelParts = rcaLabel.split("=");
        String labelKey = labelParts[0];
        String labelValue = labelParts.length > 1 ? labelParts[1] : "";

        // Find pods with the specified label across all namespaces
        LOG.info("Searching for pods with label: " + rcaLabel);
        List<Pod> pods = kubernetesClient.pods().inAnyNamespace()
                .withLabel(labelKey, labelValue)
                .list()
                .getItems();

        if (pods.isEmpty()) {
            LOG.info("No pods found with label: " + rcaLabel);
            return 0;
        }

        LOG.info("Found " + pods.size() + " pods to analyze.");

        int successCount = 0;
        int errorCount = 0;

        for (Pod pod : pods) {
            String namespace = pod.getMetadata().getNamespace();
            String podName = pod.getMetadata().getName();
            
            try {
                LOG.info(">>> Starting analysis for pod: " + namespace + "/" + podName);
                var session = rcaOrchestrator.startAnalysis(namespace, podName);
                LOG.info("<<< Analysis started for pod: " + podName + ". Session: " + session.sessionId);
                successCount++;
            } catch (Exception e) {
                LOG.error("!!! Error analyzing pod " + namespace + "/" + podName, e);
                errorCount++;
            }
        }

        LOG.info("Workload scan complete. Success: " + successCount + ", Errors: " + errorCount);
        return successCount;
    }

    /**
     * Scans a specific namespace for pods with the configured label.
     * <p>
     * This method is similar to {@link #scanWorkloads()} but limits the scan
     * to a specific namespace. Useful for targeted analysis or when working
     * with namespace-specific permissions.
     * </p>
     *
     * @param namespace the Kubernetes namespace to scan
     * @return the number of pods successfully analyzed in the namespace
     */
    public int scanNamespace(String namespace) {
        LOG.info("Starting workload scan for namespace: " + namespace);

        String[] labelParts = rcaLabel.split("=");
        String labelKey = labelParts[0];
        String labelValue = labelParts.length > 1 ? labelParts[1] : "";

        List<Pod> pods = kubernetesClient.pods().inNamespace(namespace)
                .withLabel(labelKey, labelValue)
                .list()
                .getItems();

        if (pods.isEmpty()) {
            LOG.info("No pods found in namespace " + namespace + " with label: " + rcaLabel);
            return 0;
        }

        LOG.info("Found " + pods.size() + " pods to analyze in namespace: " + namespace);

        int successCount = 0;
        int errorCount = 0;

        for (Pod pod : pods) {
            String podName = pod.getMetadata().getName();
            
            try {
                LOG.info(">>> Starting analysis for pod: " + namespace + "/" + podName);
                var session = rcaOrchestrator.startAnalysis(namespace, podName);
                LOG.info("<<< Analysis started for pod: " + podName + ". Session: " + session.sessionId);
                successCount++;
            } catch (Exception e) {
                LOG.error("!!! Error analyzing pod " + namespace + "/" + podName, e);
                errorCount++;
            }
        }

        LOG.info("Namespace scan complete. Success: " + successCount + ", Errors: " + errorCount);
        return successCount;
    }

    /**
     * Analyzes a specific pod by name and namespace.
     * <p>
     * This method provides direct analysis of a single pod without label filtering.
     * Useful for on-demand analysis triggered by webhooks or manual requests.
     * </p>
     *
     * @param namespace the Kubernetes namespace of the pod
     * @param podName the name of the pod to analyze
     * @return {@code true} if analysis completed successfully, {@code false} otherwise
     */
    public boolean analyzePod(String namespace, String podName) {
        try {
            LOG.info(">>> Starting direct analysis for pod: " + namespace + "/" + podName);
            var session = rcaOrchestrator.startAnalysis(namespace, podName);
            LOG.info("<<< Analysis started for pod: " + podName + ". Session: " + session.sessionId);
            return true;
        } catch (Exception e) {
            LOG.error("!!! Error analyzing pod " + namespace + "/" + podName, e);
            return false;
        }
    }

    /**
     * Gets the configured label selector.
     * <p>
     * Useful for testing and monitoring purposes.
     * </p>
     *
     * @return the configured label selector string
     */
    public String getRcaLabel() {
        return rcaLabel;
    }
}

// Made with Bob
