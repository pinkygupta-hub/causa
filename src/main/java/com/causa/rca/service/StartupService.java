package com.causa.rca.service;

import com.causa.rca.repository.RcaAnalysisRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Service responsible for application startup initialization.
 * <p>
 * This service handles all initialization tasks that need to occur when the
 * application starts, including:
 * <ul>
 *   <li>RAG (Retrieval-Augmented Generation) document ingestion</li>
 *   <li>Configuration validation and logging</li>
 *   <li>Mode-specific initialization messages</li>
 *   <li>System health checks</li>
 * </ul>
 * </p>
 * <p>
 * By separating startup logic into its own service, we achieve:
 * <ul>
 *   <li><b>Single Responsibility:</b> Focused only on initialization</li>
 *   <li><b>Testability:</b> Easy to test startup logic in isolation</li>
 *   <li><b>Maintainability:</b> Clear location for all startup-related code</li>
 *   <li><b>Extensibility:</b> Easy to add new initialization steps</li>
 * </ul>
 * </p>
 *
 * @see RagService
 * @see LifecycleService
 */
@ApplicationScoped
public class StartupService {

    private static final Logger LOG = Logger.getLogger(StartupService.class);

    @Inject
    RagService ragService;

    @Inject
    RcaAnalysisRepository analysisRepository;
    
    @Inject
    AnalysisTrackingService trackingService;

    @ConfigProperty(name = "cryostat.enabled", defaultValue = "false")
    boolean cryostatEnabled;

    @ConfigProperty(name = "rag.enabled", defaultValue = "true")
    boolean ragEnabled;

    @ConfigProperty(name = "rca.mode", defaultValue = "MONITORING")
    String rcaMode;

    @ConfigProperty(name = "quarkus.mongodb.connection-string")
    String mongoConnectionString;

    /**
     * Handles application startup event.
     * <p>
     * This method is automatically invoked by Quarkus when the application starts.
     * It performs all necessary initialization tasks in a specific order to ensure
     * the application is ready to process requests.
     * </p>
     * <p>
     * Initialization sequence:
     * <ol>
     *   <li>Log startup banner</li>
     *   <li>Log current configuration</li>
     *   <li>Initialize RAG system (if enabled)</li>
     *   <li>Log mode-specific information</li>
     *   <li>Log completion message</li>
     * </ol>
     * </p>
     *
     * @param ev the startup event (automatically provided by Quarkus)
     */
    void onStart(@Observes StartupEvent ev) {
        logStartupBanner();
        logConfiguration();
        validateMongoDBConnection();
        handleStuckAnalyses();
        initializeRag();
        logModeInformation();
        logStartupComplete();
    }

    /**
     * Logs the startup banner.
     * <p>
     * Provides a clear visual indicator in logs that the application is starting.
     * </p>
     */
    private void logStartupBanner() {
        LOG.info("╔════════════════════════════════════════════════════════════╗");
        LOG.info("║          RCA Agent - Root Cause Analysis System            ║");
        LOG.info("║                    Starting Up...                          ║");
        LOG.info("╚════════════════════════════════════════════════════════════╝");
    }

    /**
     * Logs the current configuration.
     * <p>
     * Displays key configuration values to help with troubleshooting and
     * verification of settings.
     * </p>
     */
    private void logConfiguration() {
        LOG.info("=== Configuration ===");
        LOG.info("RCA Mode: " + rcaMode);
        LOG.info("RAG Enabled: " + ragEnabled);
        LOG.info("Cryostat Enabled: " + cryostatEnabled);
        
        // Log MongoDB connection info (without credentials)
        String sanitizedConnection = mongoConnectionString.replaceAll("://[^@]+@", "://***:***@");
        LOG.info("MongoDB Connection: " + sanitizedConnection);
    }

    /**
     * Validates MongoDB connection and database setup.
     * <p>
     * Ensures the database is accessible before marking the application as ready.
     * This prevents the application from accepting requests when the database is unavailable.
     * </p>
     */
    private void validateMongoDBConnection() {
        LOG.info("=== MongoDB Connection Validation ===");
        try {
            // Test connection by attempting to count documents
            long count = analysisRepository.count();
            LOG.info("✓ MongoDB connection successful");
            LOG.info("  → Database: causa_rca");
            LOG.info("  → Existing analyses: " + count);
            
            // Verify retryWrites setting
            if (mongoConnectionString.contains("retryWrites=false")) {
                LOG.info("✓ Retryable writes disabled (required for standalone MongoDB)");
            } else {
                LOG.warn("⚠ Warning: retryWrites not explicitly disabled");
                LOG.warn("  → This may cause issues with standalone MongoDB instances");
                LOG.warn("  → Add '?retryWrites=false' to connection string if you encounter errors");
            }
            
        } catch (Exception e) {
            LOG.error("✗ MongoDB connection FAILED", e);
            LOG.error("═══════════════════════════════════════════════════════════");
            LOG.error("  CRITICAL ERROR: Cannot connect to MongoDB");
            LOG.error("  The application will start but database operations will fail");
            LOG.error("  Please check:");
            LOG.error("    1. MongoDB is running and accessible");
            LOG.error("    2. Connection string is correct");
            LOG.error("    3. Credentials are valid");
            LOG.error("    4. Network connectivity");
            LOG.error("═══════════════════════════════════════════════════════════");
            throw new RuntimeException("MongoDB connection validation failed. Application cannot start.", e);
        }
    }

    /**
     * Handles stuck analyses from previous application runs.
     * <p>
     * When the application restarts, any analyses that were in progress will be stuck
     * in an incomplete state. This method finds all such analyses and marks them as failed
     * with an appropriate error message.
     * </p>
     */
    private void handleStuckAnalyses() {
        LOG.info("=== Checking for Stuck Analyses ===");
        try {
            // Find all analyses that are in progress states
            var stuckAnalyses = analysisRepository.list(
                "status in ?1",
                java.util.Arrays.asList(
                    com.causa.rca.model.AnalysisStatus.INITIATED,
                    com.causa.rca.model.AnalysisStatus.COLLECTING_DATA,
                    com.causa.rca.model.AnalysisStatus.DETECTING_ANOMALY,
                    com.causa.rca.model.AnalysisStatus.ANALYZING_RCA,
                    com.causa.rca.model.AnalysisStatus.VALIDATING
                )
            );
            
            if (stuckAnalyses.isEmpty()) {
                LOG.info("✓ No stuck analyses found");
            } else {
                LOG.info("Found " + stuckAnalyses.size() + " stuck analysis(es). Marking as failed...");
                
                for (var analysis : stuckAnalyses) {
                    String errorMsg = "Analysis interrupted due to application restart. " +
                                    "Last known stage: " + analysis.status.getDisplayName();
                    trackingService.failSession(analysis.sessionId, errorMsg);
                    LOG.info("  → Marked session " + analysis.sessionId + " as failed");
                }
                
                LOG.info("✓ Successfully handled " + stuckAnalyses.size() + " stuck analysis(es)");
            }
        } catch (Exception e) {
            LOG.error("Error handling stuck analyses. Continuing startup...", e);
        }
    }

    /**
     * Initializes the RAG (Retrieval-Augmented Generation) system.
     * <p>
     * If RAG is enabled, triggers document ingestion from the knowledge base.
     * This allows the AI models to access domain-specific information for
     * enhanced analysis.
     * </p>
     */
    private void initializeRag() {
        LOG.info("=== RAG Initialization ===");
        if (ragEnabled) {
            LOG.info("RAG feature is ENABLED. Triggering document ingestion...");
            try {
                ragService.ingestDocuments();
                LOG.info("RAG initialization completed successfully.");
            } catch (Exception e) {
                LOG.error("RAG initialization failed. Continuing without RAG support.", e);
            }
        } else {
            LOG.info("RAG feature is DISABLED.");
        }
    }

    /**
     * Logs mode-specific information.
     * <p>
     * Provides clear information about how the system will operate based on
     * the configured mode (MONITORING or ALERT_DRIVEN).
     * </p>
     */
    private void logModeInformation() {
        LOG.info("=== Mode Configuration ===");
        
        if ("MONITORING".equalsIgnoreCase(rcaMode)) {
            LOG.info("✓ MONITORING mode enabled");
            LOG.info("  → Scheduled scanner will run at configured intervals");
            LOG.info("  → Pods with configured labels will be automatically analyzed");
            LOG.info("  → Manual analysis via REST API is also available");
        } else if ("ALERT_DRIVEN".equalsIgnoreCase(rcaMode)) {
            LOG.info("✓ ALERT_DRIVEN mode enabled");
            LOG.info("  → RCA will be triggered via webhook endpoint: /rca/webhook");
            LOG.info("  → Scheduled scanner is DISABLED");
            LOG.info("  → Waiting for alerts from Prometheus/Alertmanager");
            LOG.info("  → Manual analysis via REST API is also available");
        } else {
            LOG.warn("⚠ Unknown RCA mode: " + rcaMode);
            LOG.warn("  → Defaulting to MONITORING mode behavior");
            LOG.warn("  → Please check configuration: rca.mode should be MONITORING or ALERT_DRIVEN");
        }
    }

    /**
     * Logs startup completion message.
     * <p>
     * Indicates that all initialization is complete and the system is ready
     * to process requests.
     * </p>
     */
    private void logStartupComplete() {
        LOG.info("╔════════════════════════════════════════════════════════════╗");
        LOG.info("║        RCA Agent Successfully Initialized                  ║");
        LOG.info("║              Ready to Process Requests                     ║");
        LOG.info("╚════════════════════════════════════════════════════════════╝");
    }

    /**
     * Gets the current RCA mode.
     * <p>
     * Useful for testing and monitoring purposes.
     * </p>
     *
     * @return the current RCA mode (MONITORING or ALERT_DRIVEN)
     */
    public String getRcaMode() {
        return rcaMode;
    }

    /**
     * Checks if RAG is enabled.
     * <p>
     * Useful for testing and conditional logic.
     * </p>
     *
     * @return {@code true} if RAG is enabled, {@code false} otherwise
     */
    public boolean isRagEnabled() {
        return ragEnabled;
    }

    /**
     * Checks if Cryostat is enabled.
     * <p>
     * Useful for testing and conditional logic.
     * </p>
     *
     * @return {@code true} if Cryostat is enabled, {@code false} otherwise
     */
    public boolean isCryostatEnabled() {
        return cryostatEnabled;
    }
}

