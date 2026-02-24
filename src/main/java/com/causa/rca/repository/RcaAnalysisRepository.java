package com.causa.rca.repository;

import com.causa.rca.model.RcaAnalysisSession;
import com.causa.rca.model.AnalysisStatus;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing RCA analysis sessions in MongoDB.
 * <p>
 * This repository provides data access methods for querying, filtering, and
 * managing analysis sessions. It uses Quarkus Panache for simplified MongoDB
 * operations with a clean, type-safe API.
 * </p>
 * <p>
 * Key features:
 * <ul>
 *   <li>Query by status, namespace, pod name</li>
 *   <li>Pagination support for large result sets</li>
 *   <li>Efficient filtering and sorting</li>
 *   <li>Cleanup operations for old analyses</li>
 * </ul>
 * </p>
 *
 * @see RcaAnalysisSession
 * @see AnalysisStatus
 */
@ApplicationScoped
public class RcaAnalysisRepository implements PanacheMongoRepository<RcaAnalysisSession> {
    
    /**
     * Finds all analyses with a specific status.
     *
     * @param status the status to filter by
     * @return list of matching analyses, sorted by timestamp descending
     */
    public List<RcaAnalysisSession> findByStatus(AnalysisStatus status) {
        return list("status", Sort.descending("timestamp"), status);
    }
    
    /**
     * Finds all analyses for a specific namespace.
     *
     * @param namespace the Kubernetes namespace
     * @return list of matching analyses, sorted by timestamp descending
     */
    public List<RcaAnalysisSession> findByNamespace(String namespace) {
        return list("namespace", Sort.descending("timestamp"), namespace);
    }
    
    /**
     * Finds all analyses for a specific pod.
     *
     * @param namespace the Kubernetes namespace
     * @param podName the pod name
     * @return list of matching analyses, sorted by timestamp descending
     */
    public List<RcaAnalysisSession> findByPod(String namespace, String podName) {
        return list("namespace = ?1 and podName = ?2", 
                   Sort.descending("timestamp"), 
                   namespace, podName);
    }
    
    /**
     * Finds the most recent analyses with pagination support.
     *
     * @param page page number (0-based)
     * @param pageSize number of results per page
     * @return list of analyses, sorted by timestamp descending
     */
    public List<RcaAnalysisSession> findRecent(int page, int pageSize) {
        return findAll(Sort.descending("timestamp"))
                .page(page, pageSize)
                .list();
    }
    
    /**
     * Finds all analyses with optional filters and pagination.
     *
     * @param status optional status filter
     * @param namespace optional namespace filter
     * @param podName optional pod name filter
     * @param page page number (0-based)
     * @param pageSize number of results per page
     * @return list of matching analyses
     */
    public List<RcaAnalysisSession> findWithFilters(AnalysisStatus status, 
                                                     String namespace, 
                                                     String podName,
                                                     int page, 
                                                     int pageSize) {
        StringBuilder query = new StringBuilder();
        Object[] params = new Object[3];
        int paramIndex = 0;
        
        if (status != null) {
            query.append("status = ?").append(++paramIndex);
            params[paramIndex - 1] = status;
        }
        
        if (namespace != null && !namespace.isEmpty()) {
            if (query.length() > 0) query.append(" and ");
            query.append("namespace = ?").append(++paramIndex);
            params[paramIndex - 1] = namespace;
        }
        
        if (podName != null && !podName.isEmpty()) {
            if (query.length() > 0) query.append(" and ");
            query.append("podName = ?").append(++paramIndex);
            params[paramIndex - 1] = podName;
        }
        
        if (query.length() == 0) {
            return findRecent(page, pageSize);
        }
        
        // Trim params array to actual size
        Object[] actualParams = new Object[paramIndex];
        System.arraycopy(params, 0, actualParams, 0, paramIndex);
        
        return find(query.toString(), Sort.descending("timestamp"), actualParams)
                .page(page, pageSize)
                .list();
    }
    
    /**
     * Finds an analysis by its session ID.
     *
     * @param sessionId the unique session identifier
     * @return Optional containing the analysis if found
     */
    public Optional<RcaAnalysisSession> findBySessionId(String sessionId) {
        return find("sessionId", sessionId).firstResultOptional();
    }
    
    /**
     * Counts analyses by status.
     *
     * @param status the status to count
     * @return number of analyses with the given status
     */
    public long countByStatus(AnalysisStatus status) {
        return count("status", status);
    }
    
    /**
     * Counts all in-progress analyses.
     *
     * @return number of analyses currently in progress
     */
    public long countInProgress() {
        return count("status in ?1", List.of(
            AnalysisStatus.INITIATED,
            AnalysisStatus.COLLECTING_DATA,
            AnalysisStatus.DETECTING_ANOMALY,
            AnalysisStatus.ANALYZING_RCA,
            AnalysisStatus.VALIDATING
        ));
    }
    
    /**
     * Deletes analyses older than the specified cutoff date.
     * Used by the cleanup service to maintain database size.
     *
     * @param cutoffDate analyses before this date will be deleted
     * @return number of analyses deleted
     */
    public long deleteOlderThan(LocalDateTime cutoffDate) {
        return delete("timestamp < ?1", cutoffDate);
    }
    
    /**
     * Finds all in-progress analyses.
     * Useful for monitoring and debugging.
     *
     * @return list of in-progress analyses
     */
    public List<RcaAnalysisSession> findInProgress() {
        return list("status in ?1", 
                   Sort.descending("timestamp"),
                   List.of(
                       AnalysisStatus.INITIATED,
                       AnalysisStatus.COLLECTING_DATA,
                       AnalysisStatus.DETECTING_ANOMALY,
                       AnalysisStatus.ANALYZING_RCA,
                       AnalysisStatus.VALIDATING
                   ));
    }
    
    /**
     * Gets total count of all analyses.
     *
     * @return total number of analyses in the database
     */
    public long getTotalCount() {
        return count();
    }
}

// Made with Bob
