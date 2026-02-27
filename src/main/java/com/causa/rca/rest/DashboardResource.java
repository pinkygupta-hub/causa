package com.causa.rca.rest;

import com.causa.rca.model.RcaAnalysisSession;
import com.causa.rca.service.AnalysisTrackingService;
import com.causa.rca.service.ScannerService;
import com.causa.rca.service.RcaOrchestrator;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
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
 * Resource controller for serving the RCA Dashboard UI.
 * <p>
 * This controller uses Qute templates to render server-side HTML pages
 * for the dashboard interface. It provides:
 * <ul>
 *   <li>Main dashboard page with analysis list</li>
 *   <li>Individual analysis details page</li>
 *   <li>API endpoints for workloads and scanner operations</li>
 * </ul>
 * </p>
 *
 * @see AnalysisTrackingService
 * @see RcaAnalysisSession
 */
@Path("/dashboard")
public class DashboardResource {
    
    private static final Logger LOG = Logger.getLogger(DashboardResource.class);
    
    @Inject
    Template dashboard;
    
    @Inject
    Template analysisDetails;
    
    @Inject
    AnalysisTrackingService trackingService;
    
    @Inject
    ScannerService scannerService;
    
    @Inject
    RcaOrchestrator rcaOrchestrator;
    
    @Inject
    KubernetesClient kubernetesClient;
    
    @ConfigProperty(name = "rca.dashboard.show-proposed-solution", defaultValue = "false")
    boolean showProposedSolution;
    
    @ConfigProperty(name = "rca.dashboard.page-size", defaultValue = "50")
    int defaultPageSize;
    
    @ConfigProperty(name = "rca.label")
    String rcaLabel;
    
    @ConfigProperty(name = "quarkus.langchain4j.ollama.detector.chat-model.model-id", defaultValue = "phi3:mini")
    String detectorModel;
    
    @ConfigProperty(name = "quarkus.langchain4j.ollama.rca.chat-model.model-id", defaultValue = "phi3:mini")
    String rcaModel;
    
    @ConfigProperty(name = "quarkus.langchain4j.ollama.validator.chat-model.model-id", defaultValue = "phi3:mini")
    String validatorModel;
    
    /**
     * Renders the main dashboard page.
     *
     * @param page page number (0-based)
     * @return rendered HTML dashboard page
     */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance getDashboard(
            @QueryParam("page") @DefaultValue("0") int page) {
        
        LOG.infof("Rendering dashboard page %d", page);
        
        try {
            // Get recent analyses
            List<RcaAnalysisSession> analyses = trackingService.getRecentAnalyses(page, defaultPageSize);
            
            // Get statistics
            AnalysisTrackingService.AnalysisStats stats = trackingService.getStatistics();
            
            // Render template
            return dashboard
                    .data("analyses", analyses)
                    .data("stats", stats)
                    .data("page", page)
                    .data("pageSize", defaultPageSize)
                    .data("hasNext", analyses.size() == defaultPageSize)
                    .data("hasPrevious", page > 0);
                    
        } catch (Exception e) {
            LOG.error("Error rendering dashboard", e);
            // Return error page or redirect
            return dashboard
                    .data("error", "Failed to load dashboard: " + e.getMessage())
                    .data("analyses", List.of())
                    .data("stats", new AnalysisTrackingService.AnalysisStats())
                    .data("page", 0)
                    .data("pageSize", defaultPageSize)
                    .data("hasNext", false)
                    .data("hasPrevious", false);
        }
    }
    
    /**
     * Renders the analysis details page for a specific session.
     *
     * @param sessionId the unique session identifier
     * @return rendered HTML details page or 404 if not found
     */
    @GET
    @Path("/analysis/{sessionId}")
    @Produces(MediaType.TEXT_HTML)
    public Response getAnalysisDetails(@PathParam("sessionId") String sessionId) {
        LOG.infof("Rendering analysis details for session %s", sessionId);
        
        try {
            Optional<RcaAnalysisSession> sessionOpt = trackingService.getSession(sessionId);
            
            if (sessionOpt.isEmpty()) {
                LOG.warnf("Analysis not found: %s", sessionId);
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("<html><body><h1>Analysis Not Found</h1><p>Session ID: " + sessionId + "</p></body></html>")
                        .build();
            }
            
            RcaAnalysisSession session = sessionOpt.get();
            
            // Render template
            TemplateInstance template = analysisDetails
                    .data("session", session)
                    .data("showProposedSolution", showProposedSolution)
                    .data("detectorModel", detectorModel)
                    .data("rcaModel", rcaModel)
                    .data("validatorModel", validatorModel);
            
            return Response.ok(template.render()).build();
            
        } catch (Exception e) {
            LOG.error("Error rendering analysis details for " + sessionId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("<html><body><h1>Error</h1><p>" + e.getMessage() + "</p></body></html>")
                    .build();
        }
    }
    
    /**
     * API endpoint to get all workloads (pods with RCA label).
     * 
     * @return JSON list of workloads
     */
    @GET
    @Path("/api/workloads")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getWorkloads() {
        LOG.info("Fetching workloads with RCA label");
        
        try {
            String[] labelParts = rcaLabel.split("=");
            String labelKey = labelParts[0];
            String labelValue = labelParts.length > 1 ? labelParts[1] : "";
            
            // Find pods with the specified label across all namespaces
            List<Pod> pods = kubernetesClient.pods().inAnyNamespace()
                    .withLabel(labelKey, labelValue)
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
    
    /**
     * API endpoint to trigger workload scan.
     * 
     * @return JSON response with scan results
     */
    @POST
    @Path("/api/scanner/scan")
    @Produces(MediaType.APPLICATION_JSON)
    public Response triggerScan() {
        LOG.info("Triggering workload scan");
        
        try {
            int count = scannerService.scanWorkloads();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("count", count);
            result.put("message", "Analysis triggered for " + count + " pods");
            
            return Response.ok(result).build();
            
        } catch (Exception e) {
            LOG.error("Error triggering scan", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to trigger scan: " + e.getMessage()))
                    .build();
        }
    }
    
    /**
     * API endpoint to analyze a specific pod.
     * 
     * @param request JSON request with namespace and podName
     * @return JSON response with session details
     */
    @POST
    @Path("/api/scanner/analyze")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response analyzePod(Map<String, String> request) {
        String namespace = request.get("namespace");
        String podName = request.get("podName");
        
        LOG.infof("Triggering analysis for pod: %s/%s", namespace, podName);
        
        if (namespace == null || podName == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "namespace and podName are required"))
                    .build();
        }
        
        try {
            RcaAnalysisSession session = rcaOrchestrator.startAnalysis(namespace, podName);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("sessionId", session.sessionId);
            result.put("namespace", namespace);
            result.put("podName", podName);
            result.put("message", "Analysis started successfully");
            
            return Response.ok(result).build();
            
        } catch (Exception e) {
            LOG.error("Error analyzing pod " + namespace + "/" + podName, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to analyze pod: " + e.getMessage()))
                    .build();
        }
    }
}
