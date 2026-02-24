package com.causa.rca.rest;

import com.causa.rca.model.RcaAnalysisSession;
import com.causa.rca.model.AnalysisStatus;
import com.causa.rca.service.AnalysisTrackingService;
import com.causa.rca.service.ScannerService;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST API resource for querying RCA analysis data.
 * <p>
 * This resource provides JSON endpoints for the dashboard to retrieve
 * analysis sessions, statistics, and individual analysis details.
 * </p>
 * <p>
 * All endpoints return JSON and support filtering, pagination, and sorting.
 * </p>
 *
 * @see AnalysisTrackingService
 * @see RcaAnalysisSession
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AnalysisApiResource {
    
    private static final Logger LOG = Logger.getLogger(AnalysisApiResource.class);
    
    @Inject
    AnalysisTrackingService trackingService;
    
    @Inject
    KubernetesClient kubernetesClient;
    
    @ConfigProperty(name = "rca.dashboard.page-size", defaultValue = "50")
    int defaultPageSize;
    
    @ConfigProperty(name = "rca.label")
    String rcaLabel;
    
    /**
     * Lists all analyses with optional filtering and pagination.
     *
     * @param statusParam optional status filter
     * @param namespace optional namespace filter
     * @param podName optional pod name filter
     * @param page page number (0-based)
     * @param pageSize number of results per page
     * @return list of analyses matching the filters
     */
    @GET
    @Path("/analyses")
    public Response listAnalyses(
            @QueryParam("status") String statusParam,
            @QueryParam("namespace") String namespace,
            @QueryParam("pod") String podName,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("pageSize") @DefaultValue("50") int pageSize) {
        
        LOG.infof("Listing analyses: status=%s, namespace=%s, pod=%s, page=%d, pageSize=%d",
                 statusParam, namespace, podName, page, pageSize);
        
        try {
            // Parse status if provided
            AnalysisStatus status = null;
            if (statusParam != null && !statusParam.isEmpty()) {
                try {
                    status = AnalysisStatus.valueOf(statusParam.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "Invalid status: " + statusParam))
                            .build();
                }
            }
            
            // Validate pagination parameters
            if (page < 0) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Page must be >= 0"))
                        .build();
            }
            if (pageSize < 1 || pageSize > 100) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Page size must be between 1 and 100"))
                        .build();
            }
            
            // Get analyses with filters
            List<RcaAnalysisSession> analyses = trackingService.getAnalysesWithFilters(
                status, namespace, podName, page, pageSize
            );
            
            // Build response with metadata
            Map<String, Object> response = new HashMap<>();
            response.put("analyses", analyses);
            response.put("page", page);
            response.put("pageSize", pageSize);
            response.put("count", analyses.size());
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            LOG.error("Error listing analyses", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to retrieve analyses: " + e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Gets a specific analysis by its session ID.
     *
     * @param sessionId the unique session identifier
     * @return the analysis session or 404 if not found
     */
    @GET
    @Path("/analyses/{sessionId}")
    public Response getAnalysis(@PathParam("sessionId") String sessionId) {
        LOG.infof("Getting analysis: %s", sessionId);
        
        try {
            Optional<RcaAnalysisSession> session = trackingService.getSession(sessionId);
            
            if (session.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Analysis not found: " + sessionId))
                        .build();
            }
            
            return Response.ok(session.get()).build();
            
        } catch (Exception e) {
            LOG.error("Error getting analysis " + sessionId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to retrieve analysis: " + e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Gets dashboard statistics.
     *
     * @return statistics object
     */
    @GET
    @Path("/analyses/stats")
    public Response getStatistics() {
        LOG.info("Getting dashboard statistics");
        
        try {
            AnalysisTrackingService.AnalysisStats stats = trackingService.getStatistics();
            
            Map<String, Object> response = new HashMap<>();
            response.put("total", stats.total);
            response.put("inProgress", stats.inProgress);
            response.put("completed", stats.completed);
            response.put("failed", stats.failed);
            response.put("healthy", stats.healthy);
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            LOG.error("Error getting statistics", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to retrieve statistics: " + e.getMessage()))
                    .build();
        }
    }
    
    /**
     * Gets all workloads (all pods across all namespaces).
     *
     * @return list of workloads
     */
    @GET
    @Path("/workloads")
    public Response getWorkloads() {
        LOG.info("Fetching all workloads");
        
        try {
            // Find all pods across all namespaces
            List<Pod> pods = kubernetesClient.pods().inAnyNamespace()
                    .list()
                    .getItems();
            
            List<Map<String, Object>> workloads = new ArrayList<>();
            
            for (Pod pod : pods) {
                Map<String, Object> workload = new HashMap<>();
                workload.put("namespace", pod.getMetadata().getNamespace());
                workload.put("podName", pod.getMetadata().getName());
                workload.put("status", pod.getStatus().getPhase());
                
                // Get restart count
                int restarts = 0;
                if (pod.getStatus().getContainerStatuses() != null) {
                    restarts = pod.getStatus().getContainerStatuses().stream()
                            .mapToInt(cs -> cs.getRestartCount())
                            .sum();
                }
                workload.put("restarts", restarts);
                
                workloads.add(workload);
            }
            
            LOG.infof("Found %d workloads", workloads.size());
            return Response.ok(workloads).build();
            
        } catch (Exception e) {
            LOG.error("Error fetching workloads", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to fetch workloads: " + e.getMessage()))
                    .build();
        }
    }
}

// Made with Bob
