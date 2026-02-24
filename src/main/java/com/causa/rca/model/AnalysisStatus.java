package com.causa.rca.model;

/**
 * Enumeration representing the various states of an RCA analysis session.
 * <p>
 * This enum tracks the progression of an analysis from initiation through completion,
 * allowing the dashboard to display real-time status updates to users.
 * </p>
 */
public enum AnalysisStatus {
    /**
     * Analysis session has been created and is waiting to start.
     */
    INITIATED,
    
    /**
     * Currently gathering metrics, logs, events, and JFR data from Kubernetes and monitoring systems.
     */
    COLLECTING_DATA,
    
    /**
     * AI model is analyzing collected data to detect anomalies.
     */
    DETECTING_ANOMALY,
    
    /**
     * Performing root cause analysis using AI to determine the underlying issue.
     */
    ANALYZING_RCA,
    
    /**
     * Validating and formatting the analysis results into a structured report.
     */
    VALIDATING,
    
    /**
     * Analysis completed successfully with findings.
     */
    COMPLETED,
    
    /**
     * Analysis failed due to an error during processing.
     */
    FAILED,
    
    /**
     * Analysis completed but no issues were detected - system is healthy.
     */
    HEALTHY;
    
    /**
     * Calculates the progress percentage for this status.
     * Used for displaying progress bars in the UI.
     *
     * @return progress percentage (0-100), or null for FAILED status
     */
    public Double getProgressPercent() {
        return switch (this) {
            case INITIATED -> 0.0;
            case COLLECTING_DATA -> 20.0;
            case DETECTING_ANOMALY -> 40.0;
            case ANALYZING_RCA -> 60.0;
            case VALIDATING -> 80.0;
            case COMPLETED, HEALTHY -> 100.0;
            case FAILED -> null;
        };
    }
    
    /**
     * Returns a user-friendly display name for this status.
     *
     * @return display name for UI
     */
    public String getDisplayName() {
        return switch (this) {
            case INITIATED -> "Initiated";
            case COLLECTING_DATA -> "Collecting Data";
            case DETECTING_ANOMALY -> "Detecting Anomaly";
            case ANALYZING_RCA -> "Analyzing Root Cause";
            case VALIDATING -> "Validating Results";
            case COMPLETED -> "Completed";
            case FAILED -> "Failed";
            case HEALTHY -> "Healthy";
        };
    }
    
    /**
     * Returns the CSS class for styling this status in the UI.
     *
     * @return CSS class name
     */
    public String getCssClass() {
        return switch (this) {
            case INITIATED, COLLECTING_DATA, DETECTING_ANOMALY, ANALYZING_RCA, VALIDATING -> "status-in-progress";
            case COMPLETED -> "status-completed";
            case FAILED -> "status-failed";
            case HEALTHY -> "status-healthy";
        };
    }
    
}

// Made with Bob
