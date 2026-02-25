package com.causa.rca.ai;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * AI service interface for performing root cause analysis on detected anomalies.
 * <p>
 * This service takes a detected anomaly type and comprehensive pod context data to perform
 * deep analysis and determine the underlying root cause of the issue. It provides detailed
 * reasoning and proposes actionable solutions to remediate the problem.
 * </p>
 * <p>
 * The analysis heavily focuses on JFR (Java Flight Recorder) data when available, along with
 * metrics, logs, events, and pod status information to provide comprehensive diagnostics.
 * This is the second step in the RCA pipeline, following anomaly detection.
 * </p>
 *
 * @see AnomalyDetector
 * @see ValidationAgent
 */
@RegisterAiService(modelName = "rca")
public interface RootCauseAnalyst {

    /**
     * Analyzes the root cause of a detected anomaly and proposes a solution.
     * <p>
     * Performs deep analysis using all available context including metrics, logs, events,
     * pod status, and JFR data to determine why the anomaly occurred and what can be done
     * to fix it. The analysis includes detailed reasoning and evidence-based conclusions.
     * </p>
     *
     * @param anomalyType the type of anomaly detected (e.g., "OOM_KILLED", "CPU_THROTTLING")
     * @param fullContext the complete context string containing all collected data including
     *                    pod status, events, metrics, logs, and JFR analysis
     * @return a detailed analysis string containing the root cause explanation and proposed
     *         solution with reasoning and evidence
     */
    @UserMessage("""
        You are the Root Cause Analyst. Use all provided context to provide a thorough, evidence-based RCA and propose a concrete fix. Focus heavily on available data and correlate it with other metrics.  

        ANOMALY_TYPE: {anomalyType}  
        FULL_CONTEXT: {fullContext}  

        Your task: Determine the root cause of the anomaly and propose a solution. Include:  
        - Exact cause of the anomaly  
        - How it manifested in metrics/logs  
        - Impact on the system/application  
        - Steps to prevent recurrence  
        - Provide evidence of root cause 
        - Provide support logs for the anomaly

        - If there is insufficient information to determine a specific root cause, output the reasoning and provide a best-effort assessment using available metrics. Always add description, title, evidence and support logs.
        - Output ONLY a detailed, structured analysis and actionable fix. Avoid generic recommendations; be specific to the anomaly and context provided.
    """)
    String analyzeRootCause(@V("anomalyType") String anomalyType, @V("fullContext") String fullContext);
}
