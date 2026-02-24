package com.causa.rca.service;

import com.causa.rca.repository.RcaAnalysisRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;

/**
 * Service responsible for cleaning up old RCA analysis sessions.
 * <p>
 * This service runs on a scheduled basis (default: daily at 2 AM) to delete
 * analysis sessions older than the configured retention period. This helps
 * maintain database size and performance.
 * </p>
 * <p>
 * Configuration properties:
 * <ul>
 *   <li>{@code rca.cleanup.enabled} - Enable/disable cleanup (default: true)</li>
 *   <li>{@code rca.cleanup.retention-days} - Days to retain analyses (default: 30)</li>
 *   <li>{@code rca.cleanup.schedule} - Cron expression for schedule (default: 0 0 2 * * ?)</li>
 * </ul>
 * </p>
 *
 * @see RcaAnalysisRepository
 */
@ApplicationScoped
public class CleanupService {
    
    private static final Logger LOG = Logger.getLogger(CleanupService.class);
    
    @Inject
    RcaAnalysisRepository repository;
    
    @ConfigProperty(name = "rca.cleanup.enabled", defaultValue = "true")
    boolean cleanupEnabled;
    
    @ConfigProperty(name = "rca.cleanup.retention-days", defaultValue = "30")
    int retentionDays;
    
    /**
     * Scheduled cleanup job that runs based on the configured cron expression.
     * <p>
     * Default schedule: 0 0 2 * * ? (daily at 2:00 AM)
     * </p>
     * <p>
     * The job will:
     * <ol>
     *   <li>Check if cleanup is enabled</li>
     *   <li>Calculate the cutoff date based on retention period</li>
     *   <li>Delete all analyses older than the cutoff date</li>
     *   <li>Log the number of deleted analyses</li>
     * </ol>
     * </p>
     */
    @Scheduled(cron = "{rca.cleanup.schedule}")
    public void cleanupOldAnalyses() {
        if (!cleanupEnabled) {
            LOG.debug("Cleanup is disabled, skipping");
            return;
        }
        
        LOG.info("Starting scheduled cleanup of old RCA analyses");
        
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
            long deleted = repository.deleteOlderThan(cutoffDate);
            
            if (deleted > 0) {
                LOG.infof("Cleanup completed: deleted %d analyses older than %d days (before %s)",
                         deleted, retentionDays, cutoffDate);
            } else {
                LOG.info("Cleanup completed: no old analyses to delete");
            }
        } catch (Exception e) {
            LOG.error("Error during cleanup job", e);
        }
    }
    
    /**
     * Manually triggers a cleanup operation.
     * <p>
     * This method can be called programmatically or via an admin endpoint
     * to force a cleanup outside of the scheduled time.
     * </p>
     *
     * @return number of analyses deleted
     */
    public long manualCleanup() {
        if (!cleanupEnabled) {
            LOG.warn("Manual cleanup requested but cleanup is disabled");
            return 0;
        }
        
        LOG.info("Manual cleanup triggered");
        
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
            long deleted = repository.deleteOlderThan(cutoffDate);
            
            LOG.infof("Manual cleanup completed: deleted %d analyses", deleted);
            return deleted;
        } catch (Exception e) {
            LOG.error("Error during manual cleanup", e);
            throw new RuntimeException("Cleanup failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets the current cleanup configuration.
     *
     * @return configuration details as a formatted string
     */
    public String getConfiguration() {
        return String.format("Cleanup enabled: %s, Retention: %d days", 
                           cleanupEnabled, retentionDays);
    }
    
    /**
     * Calculates the cutoff date for cleanup.
     *
     * @return the date before which analyses will be deleted
     */
    public LocalDateTime getCutoffDate() {
        return LocalDateTime.now().minusDays(retentionDays);
    }
}

// Made with Bob
