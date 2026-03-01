package com.causa.rca.ai;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * AI service interface for performing root cause analysis on detected anomalies.
 *
 * <p>Receives a <b>compact, LLM-safe summary context</b> produced by
 * {@link com.causa.rca.model.artifact.CollectedArtifacts#toLlmContext()}.
 * This context contains only pre-processed summaries — never raw log lines,
 * raw event dumps, or full JFR reports.</p>
 *
 * <p>This is the second step in the RCA pipeline, following anomaly detection.</p>
 *
 * @see AnomalyDetector
 * @see ValidationAgent
 * @see com.causa.rca.model.artifact.CollectedArtifacts
 */
@RegisterAiService(modelName = "rca")
public interface RootCauseAnalyst {

    /**
     * Analyses the root cause of a detected anomaly and proposes a solution.
     *
     * <p>The {@code llmContext} parameter contains pre-processed, token-budgeted
     * summaries of pod status, metrics, events, and representative log lines.
     * If JFR data is available it is included as a compact summary in the
     * {@code =JFR=} section of the context.</p>
     *
     * @param anomalyType the anomaly type token from {@link AnomalyDetector}
     *                    (e.g. {@code "OOM_KILLED"}, {@code "CPU_THROTTLING"})
     * @param llmContext  the compact summary context from
     *                    {@link com.causa.rca.model.artifact.CollectedArtifacts#toLlmContext()}
     * @return a detailed analysis string with root cause reasoning and proposed fix
     */
    @UserMessage("""
            Role: Root Cause Analysis Engine.
            CRITICAL OUTPUT RULES: 1. Output MUST be structured, NO markdown, NO explanations outside fields, NO extra text
                                   2. Return EXACTLY this structure:
                                   ROOT_CAUSE: <concise summary in max 2-3 sentences>
                                   KEY_EVIDENCE: <short bullet, metrics or signals only>
                                   SUPPORTED LOGS: 
                                   <SUPPORTED_LOGS must contain ONLY verbatim log lines that appear in llmContext/Signals.
                                   If llmContext/Signals does not contain verbatim log lines, output no logs present>
            Input: ANOMALY: {anomalyType}
            Signals: {llmContext}
            Task: Select the most likely root cause.
            RULES:
                1. Use ONLY information present in signals
                2. Do NOT invent metrics or logs
            """)
    String analyzeRootCause(@V("anomalyType") String anomalyType, @V("llmContext") String llmContext);
}
