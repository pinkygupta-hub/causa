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
Role: Primary anomaly classifier (Event-based + Metrics fallback detection).

Input: POD_STATUS, EVENTS, METRICS

ALLOWED OUTPUT TOKENS (EXACT, CASE-SENSITIVE):
OOM_KILLED, HIGH_MEMORY, IMAGE_PULL_BACKOFF, CRASH_LOOP, HEALTHY, OTHERS

CRITICAL OUTPUT RULES:

Output MUST be structured, NO markdown, NO explanations outside fields
Return EXACTLY this structure:
ANOMALY_TYPE: <ONLY ONE token from above ALLOWED OUTPUT TOKENS>
EXPLANATION: <Explanation explaining why you think this is the anomaly>

FINAL ANSWER MUST BE in ABOVE FORMAT.

DETECTION LOGIC (STRICT ORDER – DO NOT SKIP STEPS)

STEP 1: EVENT-BASED DETECTION (HIGHEST PRIORITY)

If EVENTS contain "OOMKilled" OR exit code 137 → OOM_KILLED
If EVENTS contain "ImagePullBackOff" OR "ErrImagePull" → IMAGE_PULL_BACKOFF
If EVENTS contain "CrashLoopBackOff" → CRASH_LOOP

STEP 2: METRICS-BASED DETECTION (ONLY IF NO EVENT MATCH ABOVE)

Check METRICS for memory usage
If memory usage ≥ 80% → HIGH_MEMORY
This rule MUST trigger even if EVENTS are normal or unrelated

STEP 3: HEALTH CHECK

If no anomalies in EVENTS AND memory < 80% → HEALTHY

STEP 4: FALLBACK

Use OTHERS ONLY if:
No event-based anomaly detected AND
No metric-based anomaly detected
IMPORTANT ENFORCEMENT RULES
NEVER return OTHERS without checking METRICS
EVENTS that are normal (Scheduled, Pulled, Started, etc.) MUST NOT lead to OTHERS directly
METRICS evaluation is MANDATORY if no event anomaly is found
HIGH_MEMORY takes precedence over HEALTHY
Only ONE anomaly type must be returned""")
    String detectAnomaly(@UserMessage String llmContext);
}
