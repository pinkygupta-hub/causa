package com.causa.rca.service;

import com.causa.rca.model.RcaAnalysisSession;
import com.causa.rca.model.AnalysisStatus;
import com.causa.rca.model.RcaReport;
import com.causa.rca.model.artifact.CollectedArtifacts;
import com.causa.rca.repository.RcaAnalysisRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for tracking and managing RCA analysis sessions.
 * <p>
 * This service provides the business logic layer for creating, updating, and
 * querying analysis sessions. It acts as the bridge between the RCA orchestrator
 * and the MongoDB persistence layer.
 * </p>
 * <p>
 * Key responsibilities:
 * <ul>
 *   <li>Creating new analysis sessions</li>
 *   <li>Updating session status and progress</li>
 *   <li>Storing completed analysis results</li>
 *   <li>Handling analysis failures</li>
 *   <li>Providing query methods for the dashboard</li>
 * </ul>
 * </p>
 *
 * @see RcaAnalysisSession
 * @see RcaAnalysisRepository
 */
@ApplicationScoped
public class AnalysisTrackingService {
    
    private static final Logger LOG = Logger.getLogger(AnalysisTrackingService.class);
    
    @Inject
    RcaAnalysisRepository repository;
    
    /**
     * Creates a new analysis session and persists it to MongoDB.
     * <p>
     * Note: No @Transactional annotation needed. MongoDB operations are atomic
     * at the document level, and @Transactional with JTA requires retryable writes
     * which are not supported by standalone MongoDB instances.
     * </p>
     *
     * @param namespace the Kubernetes namespace
     * @param podName the pod name
     * @return the created session with generated ID and session ID
     */
    public RcaAnalysisSession createSession(String namespace, String podName) {
        RcaAnalysisSession session = new RcaAnalysisSession();
        session.sessionId = UUID.randomUUID().toString();
        session.timestamp = LocalDateTime.now();
        session.namespace = namespace;
        session.podName = podName;
        session.status = AnalysisStatus.INITIATED;
        session.currentStep = "Analysis initiated";
        session.updateProgress();
        
        repository.persist(session);
        
        LOG.infof("Created analysis session %s for %s/%s",
                 session.sessionId, namespace, podName);
        
        return session;
    }
    
    /**
     * Updates the status of an existing analysis session.
     * <p>
     * Note: No @Transactional annotation needed. MongoDB operations are atomic
     * at the document level.
     * </p>
     *
     * @param sessionId the unique session identifier
     * @param status the new status
     * @param step description of the current step
     * @return true if update was successful, false if session not found
     */
    public boolean updateStatus(String sessionId, AnalysisStatus status, String step) {
        Optional<RcaAnalysisSession> optSession = repository.findBySessionId(sessionId);
        
        if (optSession.isEmpty()) {
            LOG.warnf("Attempted to update non-existent session: %s", sessionId);
            return false;
        }
        
        RcaAnalysisSession session = optSession.get();
        session.status = status;
        session.currentStep = step;
        session.updateProgress();
        
        repository.update(session);
        
        LOG.infof("Updated session %s to status %s: %s",
                 sessionId, status, step);
        
        return true;
    }
    
    /**
     * Records the start time for a specific analysis stage.
     *
     * @param sessionId the unique session identifier
     * @param stage the stage name (e.g., "data_collection", "anomaly_detection", "rca_analysis", "validation")
     * @return true if update was successful, false if session not found
     */
    public boolean recordStageStart(String sessionId, String stage) {
        Optional<RcaAnalysisSession> optSession = repository.findBySessionId(sessionId);
        
        if (optSession.isEmpty()) {
            LOG.warnf("Attempted to record stage start for non-existent session: %s", sessionId);
            return false;
        }
        
        RcaAnalysisSession session = optSession.get();
        session.recordStageStart(stage);
        repository.update(session);
        
        LOG.infof("Recorded start of stage '%s' for session %s", stage, sessionId);
        
        return true;
    }
    
    /**
     * Records the end time for a specific analysis stage.
     *
     * @param sessionId the unique session identifier
     * @param stage the stage name
     * @return true if update was successful, false if session not found
     */
    public boolean recordStageEnd(String sessionId, String stage) {
        Optional<RcaAnalysisSession> optSession = repository.findBySessionId(sessionId);
        
        if (optSession.isEmpty()) {
            LOG.warnf("Attempted to record stage end for non-existent session: %s", sessionId);
            return false;
        }
        
        RcaAnalysisSession session = optSession.get();
        session.recordStageEnd(stage);
        repository.update(session);
        
        LOG.infof("Recorded end of stage '%s' for session %s (duration: %s)",
                 stage, sessionId, session.getFormattedStageDuration(stage));
        
        return true;
    }
    
    /**
     * Updates the anomaly type for a session.
     *
     * @param sessionId the unique session identifier
     * @param anomalyType the detected anomaly type (e.g., "CPU", "MEMORY", "OOM", "HEALTHY")
     * @return true if update was successful, false if session not found
     */
    public boolean updateAnomalyType(String sessionId, String anomalyType) {
        Optional<RcaAnalysisSession> optSession = repository.findBySessionId(sessionId);
        
        if (optSession.isEmpty()) {
            LOG.warnf("Attempted to update anomaly type for non-existent session: %s", sessionId);
            return false;
        }
        
        RcaAnalysisSession session = optSession.get();
        session.anomalyType = anomalyType;
        repository.update(session);
        
        LOG.infof("Updated anomaly type to '%s' for session %s", anomalyType, sessionId);
        
        return true;
    }
    
    /**
     * Stores the collected diagnostic artifacts on the session document.
     * <p>
     * Called immediately after data collection completes so that raw evidence
     * (logs, events, metrics, pod info) is persisted to MongoDB and available
     * for UX display regardless of whether the LLM analysis succeeds.
     * </p>
     *
     * @param sessionId the unique session identifier
     * @param artifacts the collected artifacts to persist
     * @return true if update was successful, false if session not found
     */
    public boolean storeArtifacts(String sessionId, CollectedArtifacts artifacts) {
        Optional<RcaAnalysisSession> optSession = repository.findBySessionId(sessionId);

        if (optSession.isEmpty()) {
            LOG.warnf("Attempted to store artifacts for non-existent session: %s", sessionId);
            return false;
        }

        RcaAnalysisSession session = optSession.get();
        session.collectedArtifacts = artifacts;
        repository.update(session);

        LOG.infof("Stored collected artifacts for session %s (tokens=%d, truncated=%b)",
                sessionId, artifacts.tokenCount, artifacts.truncationApplied);

        return true;
    }

    /**
     * Marks an analysis session as completed with the final report.
     * <p>
     * Note: No @Transactional annotation needed. MongoDB operations are atomic
     * at the document level.
     * </p>
     *
     * @param sessionId the unique session identifier
     * @param report the completed RCA report
     * @return true if update was successful, false if session not found
     */
    public boolean completeSession(String sessionId, RcaReport report) {
        Optional<RcaAnalysisSession> optSession = repository.findBySessionId(sessionId);
        
        if (optSession.isEmpty()) {
            LOG.warnf("Attempted to complete non-existent session: %s", sessionId);
            return false;
        }
        
        RcaAnalysisSession session = optSession.get();
        session.status = AnalysisStatus.COMPLETED;
        session.report = report;
        session.completedAt = LocalDateTime.now();
        session.currentStep = "Analysis completed successfully";
        session.updateProgress();
        
        repository.update(session);
        
        LOG.infof("Completed session %s in %s", 
                 sessionId, session.getFormattedDuration());
        
        return true;
    }
    
    /**
     * Marks an analysis session as healthy (no issues detected).
     * <p>
     * Note: No @Transactional annotation needed. MongoDB operations are atomic
     * at the document level.
     * </p>
     *
     * @param sessionId the unique session identifier
     * @param report the healthy status report
     * @return true if update was successful, false if session not found
     */
    public boolean markHealthy(String sessionId, RcaReport report) {
        Optional<RcaAnalysisSession> optSession = repository.findBySessionId(sessionId);
        
        if (optSession.isEmpty()) {
            LOG.warnf("Attempted to mark non-existent session as healthy: %s", sessionId);
            return false;
        }
        
        RcaAnalysisSession session = optSession.get();
        session.status = AnalysisStatus.HEALTHY;
        session.report = report;
        session.completedAt = LocalDateTime.now();
        session.currentStep = "No issues detected - system is healthy";
        session.updateProgress();
        
        repository.update(session);
        
        LOG.infof("Marked session %s as healthy", sessionId);
        
        return true;
    }
    
    /**
     * Marks an analysis session as failed with an error message.
     * <p>
     * Note: No @Transactional annotation needed. MongoDB operations are atomic
     * at the document level.
     * </p>
     *
     * @param sessionId the unique session identifier
     * @param errorMessage description of the error
     * @return true if update was successful, false if session not found
     */
    public boolean failSession(String sessionId, String errorMessage) {
        Optional<RcaAnalysisSession> optSession = repository.findBySessionId(sessionId);
        
        if (optSession.isEmpty()) {
            LOG.warnf("Attempted to fail non-existent session: %s", sessionId);
            return false;
        }
        
        RcaAnalysisSession session = optSession.get();
        session.status = AnalysisStatus.FAILED;
        session.errorMessage = errorMessage;
        session.completedAt = LocalDateTime.now();
        session.currentStep = "Analysis failed";
        session.updateProgress();
        
        repository.update(session);
        
        LOG.errorf("Failed session %s: %s", sessionId, errorMessage);
        
        return true;
    }
    
    /**
     * Retrieves an analysis session by its ID.
     *
     * @param sessionId the unique session identifier
     * @return Optional containing the session if found
     */
    public Optional<RcaAnalysisSession> getSession(String sessionId) {
        return repository.findBySessionId(sessionId);
    }
    
    /**
     * Retrieves recent analyses with pagination.
     *
     * @param page page number (0-based)
     * @param pageSize number of results per page
     * @return list of recent analyses
     */
    public List<RcaAnalysisSession> getRecentAnalyses(int page, int pageSize) {
        return repository.findRecent(page, pageSize);
    }
    
    /**
     * Retrieves analyses with optional filters.
     *
     * @param status optional status filter
     * @param namespace optional namespace filter
     * @param podName optional pod name filter
     * @param page page number (0-based)
     * @param pageSize number of results per page
     * @return list of matching analyses
     */
    public List<RcaAnalysisSession> getAnalysesWithFilters(AnalysisStatus status,
                                                           String namespace,
                                                           String podName,
                                                           int page,
                                                           int pageSize) {
        return repository.findWithFilters(status, namespace, podName, page, pageSize);
    }
    
    /**
     * Gets statistics about analyses for the dashboard.
     *
     * @return map containing various statistics
     */
    public AnalysisStats getStatistics() {
        AnalysisStats stats = new AnalysisStats();
        stats.total = repository.getTotalCount();
        stats.inProgress = repository.countInProgress();
        stats.completed = repository.countByStatus(AnalysisStatus.COMPLETED);
        stats.failed = repository.countByStatus(AnalysisStatus.FAILED);
        stats.healthy = repository.countByStatus(AnalysisStatus.HEALTHY);
        return stats;
    }
    
    /**
     * Retrieves completed unhealthy analyses (not HEALTHY status).
     * Used for the dashboard overview showing only problematic analyses.
     *
     * @param page page number (0-based)
     * @param pageSize number of results per page
     * @return list of completed unhealthy analyses
     */
    public List<RcaAnalysisSession> getUnhealthyAnalyses(int page, int pageSize) {
        return repository.findCompletedUnhealthy(page, pageSize);
    }
    
    /**
     * Counts completed unhealthy analyses.
     *
     * @return number of completed unhealthy analyses
     */
    public long countUnhealthyAnalyses() {
        return repository.countCompletedUnhealthy();
    }
    
    /**
     * Simple statistics holder class.
     */
    public static class AnalysisStats {
        public long total;
        public long inProgress;
        public long completed;
        public long failed;
        public long healthy;
    }
}
