package com.causa.rca.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * AI service interface for detecting anomalies in Kubernetes pods.
 *
 * <p>Receives a <b>compact, LLM-safe summary context</b> produced by
 * {@link com.causa.rca.model.artifact.CollectedArtifacts#toLlmContext()}.
 * This context contains only pre-processed summaries of metrics, pod status,
 * events, and logs — never raw log lines or full event dumps.</p>
 *
 * <p>This is the first step in the RCA pipeline.</p>
 *
 * @see RootCauseAnalyst
 * @see ValidationAgent
 * @see com.causa.rca.model.artifact.CollectedArtifacts
 */
@RegisterAiService(modelName = "detector")
public interface AnomalyDetector {

    /**
     * Detects anomalies from the compact LLM-safe summary context.
     *
     * <p>The {@code llmContext} parameter contains pre-processed, token-budgeted
     * summaries of pod status, metrics, events, and log patterns.
     * Raw logs and raw events are <b>never</b> included.</p>
     *
     * @param llmContext the compact summary context from
     *                   {@link com.causa.rca.model.artifact.CollectedArtifacts#toLlmContext()}
     * @return a single anomaly type token (e.g. {@code "OOM_KILLED"},
     *         {@code "CPU_THROTTLING"}, {@code "CRASH_LOOP"}) or {@code "HEALTHY"}
     */
    @SystemMessage("""
            Role: anomaly classifier.
            Input: POD_STATUS, METRICS
            ALLOWED OUTPUT TOKENS (EXACT, CASE-SENSITIVE):
                OOM_KILLED, GC_PAUSE, IMAGE_PULL_BACKOFF, HEALTHY, OTHERS
            CRITICAL OUTPUT RULES: 1. Output MUST be structured, NO markdown, NO explanations outside fields, NO extra text
                                   2. Return EXACTLY this structure:
                                   ANAMOLY_TYPE: <ONLY ONE token from above ALLOWED OUTPUT TOKENS>
                                   EXPLANATION: <Explanation explaining why you think think this the anamoly>
            FINAL ANSWER MUST BE in ABOVE FORMAT.
            Task: Classify system state and expected anamoly type from allowed tokens and also consider future possible anamoly type.
            For example if in future, OOM_KILLED can happen, then indicate OOM_KILLED
            """)
    String detectAnomaly(@UserMessage String llmContext);
}
