package com.causa.rca.service;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Service managing application lifecycle events and scheduled tasks.
 * <p>
 * This service handles:
 * <ul>
 *   <li>Application startup initialization</li>
 *   <li>RAG (Retrieval-Augmented Generation) document ingestion on startup</li>
 *   <li>Scheduled workload scanning for automatic RCA analysis (when in MONITORING mode)</li>
 * </ul>
 * </p>
 * <p>
 * The workload scanner runs periodically based on the configured interval
 * (rca.scan.interval property) to automatically detect and analyze pods
 * with issues. This is only active when rca.mode is set to MONITORING.
 * </p>
 * <p>
 * When rca.mode is set to ALERT_DRIVEN, the scheduler is disabled and RCA analysis
 * is triggered only via webhook calls from external alerting systems like Prometheus.
 * </p>
 *
 * @see RagService
 * @see ScannerService
 */
@ApplicationScoped
public class LifecycleService {

    private static final Logger LOG = Logger.getLogger(LifecycleService.class);

    @ConfigProperty(name = "cryostat.enabled", defaultValue = "false")
    boolean cryostatEnabled;

    @ConfigProperty(name = "rag.enabled", defaultValue = "true")
    boolean ragEnabled;

    @ConfigProperty(name = "rca.mode", defaultValue = "MONITORING")
    String rcaMode;

    @Inject
    RagService ragService;

    @Inject
    ScannerService scannerService;

    /**
     * Scheduled task that periodically scans workloads for RCA analysis.
     * <p>
     * Runs at the interval specified by the "rca.scan.interval" configuration property,
     * with an initial delay of 2 minutes after application startup. This allows the
     * system to automatically monitor and analyze pods with specific labels.
     * </p>
     * <p>
     * This scheduler is only active when rca.mode is set to MONITORING. When in ALERT_DRIVEN
     * mode, this method will not execute, and RCA analysis is triggered only via webhook calls.
     * </p>
     */
    @Scheduled(every = "${rca.scan.interval}", delayed = "2m", skipExecutionIf = LifecycleService.SkipIfNotMonitoringMode.class)
    public void scanWorkloads() {
        LOG.debug("Scheduled workload scan triggered."); // Changed to debug for less verbosity
        scannerService.scanWorkloads();
    }

    /**
     * Predicate to skip scheduled execution when not in MONITORING mode.
     * <p>
     * This allows the scheduler to be conditionally disabled based on the rca.mode configuration.
     * When rca.mode is ALERT_DRIVEN, the scheduled scanning is skipped entirely.
     * </p>
     */
    public static class SkipIfNotMonitoringMode implements Scheduled.SkipPredicate {
        @ConfigProperty(name = "rca.mode", defaultValue = "MONITORING")
        String rcaMode;

        @Override
        public boolean test(ScheduledExecution execution) {
            boolean skip = !"MONITORING".equalsIgnoreCase(rcaMode);
            if (skip) {
                Logger.getLogger(LifecycleService.class).debug("Skipping scheduled scan - RCA mode is: " + rcaMode);
            }
            return skip;
        }
    }

    /**
     * Handles application startup event.
     * <p>
     * Performs initialization tasks including:
     * <ul>
     *   <li>Ingesting RAG knowledge base documents if RAG is enabled</li>
     *   <li>Initializing the scheduled workload scanner (if in MONITORING mode)</li>
     *   <li>Logging configuration status</li>
     * </ul>
     * </p>
     *
     * @param ev the startup event (automatically provided by Quarkus)
     */
    void onStart(@Observes StartupEvent ev) {
        LOG.info("=== RCA Agent Lifecycle Started ===");
        LOG.info("RCA Mode: " + rcaMode);

        if (ragEnabled) {
            LOG.info("RAG feature is ENABLED in config. Triggering ingestion...");
            ragService.ingestDocuments();
        } else {
            LOG.info("RAG feature is DISABLED in config.");
        }

        if ("MONITORING".equalsIgnoreCase(rcaMode)) {
            LOG.info("MONITORING mode enabled - Scheduled scanner will run with 2m delay.");
        } else if ("ALERT_DRIVEN".equalsIgnoreCase(rcaMode)) {
            LOG.info("ALERT_DRIVEN mode enabled - RCA will be triggered via webhook endpoint at /rca/webhook");
            LOG.info("Scheduled scanner is DISABLED in ALERT_DRIVEN mode.");
        } else {
            LOG.warn("Unknown RCA mode: " + rcaMode + ". Defaulting to MONITORING mode behavior.");
        }

        LOG.info("=== RCA Agent Services Initialized ===");
    }
}
