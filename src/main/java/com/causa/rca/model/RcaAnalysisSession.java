package com.causa.rca.model;

import com.causa.rca.model.artifact.CollectedArtifacts;
import io.quarkus.mongodb.panache.common.MongoEntity;
import io.quarkus.mongodb.panache.PanacheMongoEntity;
import org.bson.codecs.pojo.annotations.BsonIgnore;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * MongoDB entity representing an RCA analysis session.
 * <p>
 * This entity tracks the complete lifecycle of an RCA analysis from initiation
 * through completion, storing all relevant metadata and results. It serves as
 * the primary data model for the dashboard UI.
 * </p>
 * <p>
 * The entity uses MongoDB's Panache pattern for simplified data access and
 * includes helper methods for calculating derived values like progress and duration.
 * The 'id' field is inherited from PanacheMongoEntity.
 * </p>
 *
 * @see AnalysisStatus
 * @see RcaReport
 */
@MongoEntity(collection = "rca_analyses")
public class RcaAnalysisSession extends PanacheMongoEntity {
    
    /**
     * Unique session identifier (UUID) for tracking and correlation.
     */
    public String sessionId;
    
    /**
     * Timestamp when the analysis was initiated.
     */
    public LocalDateTime timestamp;
    
    /**
     * Kubernetes namespace of the pod being analyzed.
     */
    public String namespace;
    
    /**
     * Name of the pod being analyzed.
     */
    public String podName;
    
    /**
     * Current status of the analysis.
     */
    public AnalysisStatus status;
    
    /**
     * Human-readable description of the current step being executed.
     */
    public String currentStep;
    
    /**
     * The complete RCA report (null until analysis is complete).
     */
    public RcaReport report;

    /**
     * The collected diagnostic artifacts (metrics, logs, events, pod info, optional JFR).
     * Stored after data collection completes so that the UX can display full raw evidence
     * (raw logs, raw events, raw metrics) independently of the LLM analysis.
     * <p>
     * Null until the data collection stage completes successfully.
     * </p>
     */
    public CollectedArtifacts collectedArtifacts;
    
    /**
     * Error message if the analysis failed.
     */
    public String errorMessage;
    
    /**
     * Timestamp when the analysis completed (success or failure).
     */
    public LocalDateTime completedAt;
    
    /**
     * Progress percentage (0-100) for UI display.
     * Calculated based on the current status.
     */
    public Double progressPercent;
    
    /**
     * Stage timing information - tracks start and end times for each analysis stage.
     * Map keys: "data_collection", "anomaly_detection", "memory_analysis", "rca_analysis", "validation"
     * Map values: Map with "start" and "end" LocalDateTime values
     */
    public Map<String, Map<String, LocalDateTime>> stageTiming;
    
    /**
     * Detected anomaly type (e.g., "CPU", "MEMORY", "OOM", "HEALTHY").
     * Set after anomaly detection stage completes.
     */
    public String anomalyType;
    
    /**
     * Default constructor for MongoDB deserialization.
     */
    public RcaAnalysisSession() {
        this.stageTiming = new HashMap<>();
    }
    
    /**
     * Records the start time for a specific analysis stage.
     *
     * @param stage the stage name (e.g., "data_collection", "anomaly_detection", "rca_analysis", "validation")
     */
    public void recordStageStart(String stage) {
        if (stageTiming == null) {
            stageTiming = new HashMap<>();
        }
        Map<String, LocalDateTime> timing = stageTiming.computeIfAbsent(stage, k -> new HashMap<>());
        timing.put("start", LocalDateTime.now());
    }
    
    /**
     * Records the end time for a specific analysis stage.
     *
     * @param stage the stage name
     */
    public void recordStageEnd(String stage) {
        if (stageTiming == null) {
            stageTiming = new HashMap<>();
        }
        Map<String, LocalDateTime> timing = stageTiming.computeIfAbsent(stage, k -> new HashMap<>());
        timing.put("end", LocalDateTime.now());
    }
    
    /**
     * Gets the duration for a specific stage.
     *
     * @param stage the stage name
     * @return duration of the stage, or null if not available
     */
    public Duration getStageDuration(String stage) {
        if (stageTiming == null || !stageTiming.containsKey(stage)) {
            return null;
        }
        Map<String, LocalDateTime> timing = stageTiming.get(stage);
        LocalDateTime start = timing.get("start");
        LocalDateTime end = timing.get("end");
        
        if (start == null) {
            return null;
        }
        
        LocalDateTime endTime = end != null ? end : LocalDateTime.now();
        return Duration.between(start, endTime);
    }
    
    /**
     * Gets formatted duration for a specific stage.
     *
     * @param stage the stage name
     * @return formatted duration string or "N/A"
     */
    public String getFormattedStageDuration(String stage) {
        Duration duration = getStageDuration(stage);
        if (duration == null) {
            return "N/A";
        }
        
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long remainingSeconds = seconds % 60;
            return minutes + "m " + remainingSeconds + "s";
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "h " + minutes + "m";
        }
    }
    
    /**
     * Updates the progress percentage based on the current status.
     * Should be called whenever the status changes.
     * For FAILED status, progress is set to 0.0 to indicate incomplete analysis.
     */
    public void updateProgress() {
        if (status == null) {
            this.progressPercent = 0.0;
        } else {
            Double progress = status.getProgressPercent();
            this.progressPercent = progress != null ? progress : 0.0;
        }
    }
    
    /**
     * Calculates the duration of the analysis.
     * This is a computed property and not persisted to MongoDB.
     *
     * @return duration from start to completion, or from start to now if still in progress
     */
    @BsonIgnore
    public Duration getDuration() {
        if (timestamp == null) {
            return Duration.ZERO;
        }
        LocalDateTime endTime = completedAt != null ? completedAt : LocalDateTime.now();
        return Duration.between(timestamp, endTime);
    }
    
    /**
     * Setter for duration field - ignored to handle legacy documents.
     * This allows MongoDB to deserialize documents that have a duration field
     * from previous versions without causing codec errors.
     *
     * @param duration the duration value (ignored)
     */
    @BsonIgnore
    public void setDuration(Duration duration) {
        // Intentionally empty - duration is computed, not stored
    }
    
    /**
     * Returns a formatted duration string for display.
     *
     * @return formatted duration (e.g., "45s", "2m 30s", "1h 15m")
     */
    public String getFormattedDuration() {
        Duration duration = getDuration();
        long seconds = duration.getSeconds();
        
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long remainingSeconds = seconds % 60;
            return minutes + "m " + remainingSeconds + "s";
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "h " + minutes + "m";
        }
    }
    
    /**
     * Checks if the analysis is currently in progress.
     *
     * @return true if status indicates analysis is ongoing
     */
    public boolean isInProgress() {
        return status != null && (
            status == AnalysisStatus.INITIATED ||
            status == AnalysisStatus.COLLECTING_DATA ||
            status == AnalysisStatus.DETECTING_ANOMALY ||
            status == AnalysisStatus.MEMORY_ANALYSIS ||
            status == AnalysisStatus.ANALYZING_RCA ||
            status == AnalysisStatus.VALIDATING
        );
    }
    
    /**
     * Checks if the analysis has completed (successfully or with failure).
     *
     * @return true if analysis is no longer in progress
     */
    public boolean isCompleted() {
        return status != null && (
            status == AnalysisStatus.COMPLETED ||
            status == AnalysisStatus.FAILED ||
            status == AnalysisStatus.HEALTHY
        );
    }
    
    /**
     * Gets the status CSS class for a specific stage.
     * Returns "stage-completed", "stage-in-progress", or "stage-pending".
     *
     * @param stage the stage name (e.g., "data_collection", "anomaly_detection", "memory_analysis", "rca_analysis", "validation")
     * @return CSS class for the stage status
     */
    public String getStageStatus(String stage) {
        if (stageTiming == null || !stageTiming.containsKey(stage)) {
            return "stage-pending";
        }
        
        Map<String, LocalDateTime> timing = stageTiming.get(stage);
        LocalDateTime start = timing.get("start");
        LocalDateTime end = timing.get("end");
        
        if (start == null) {
            return "stage-pending";
        }
        
        if (end != null) {
            return "stage-completed";
        }
        
        return "stage-in-progress";
    }
    
    /**
     * Gets a descriptive text for what was done in a specific stage.
     *
     * @param stage the stage name
     * @return description of what was done in the stage
     */
    public String getStageDescription(String stage) {
        String status = getStageStatus(stage);
        
        switch (stage) {
            case "data_collection":
                if ("stage-completed".equals(status)) {
                    return "Collected pod events, status, and metrics";
                } else if ("stage-in-progress".equals(status)) {
                    return "Collecting diagnostic data...";
                } else {
                    return "Pending data collection";
                }
                
            case "anomaly_detection":
                if ("stage-completed".equals(status)) {
                    String type = anomalyType != null ? anomalyType : "Unknown";
                    return "Detected anomaly: " + type;
                } else if ("stage-in-progress".equals(status)) {
                    return "Analyzing data for anomalies...";
                } else {
                    return "Pending anomaly detection";
                }
                
            case "memory_analysis":
                if ("stage-completed".equals(status)) {
                    return "Memory pressure detected, collected additional data";
                } else if ("stage-in-progress".equals(status)) {
                    return "Analyzing memory usage...";
                } else {
                    return "Pending memory analysis";
                }
                
            case "rca_analysis":
                if ("stage-completed".equals(status)) {
                    String type = anomalyType != null ? anomalyType : "detected issue";
                    return "Performed RCA for " + type;
                } else if ("stage-in-progress".equals(status)) {
                    return "Analyzing root cause...";
                } else {
                    return "Pending root cause analysis";
                }
                
            case "validation":
                if ("stage-completed".equals(status)) {
                    return "Validated RCA response";
                } else if ("stage-in-progress".equals(status)) {
                    return "Validating analysis...";
                } else {
                    return "Pending validation";
                }
                
            default:
                return "";
        }
    }
    
    /**
     * Returns a formatted timestamp for display.
     *
     * @return formatted timestamp string
     */
    public String getFormattedTimestamp() {
        if (timestamp == null) {
            return "N/A";
        }
        return timestamp.toString().replace('T', ' ');
    }
    
    /**
     * Returns the full pod identifier (namespace/podName).
     *
     * @return formatted pod identifier
     */
    public String getPodIdentifier() {
        return namespace + "/" + podName;
    }
    
}

