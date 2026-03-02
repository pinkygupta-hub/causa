package com.causa.rca.ai;

import com.causa.rca.model.RcaReport;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * AI service interface for validating and formatting RCA analysis results.
 *
 * <p>Receives a <b>compact, LLM-safe summary context</b> produced by
 * {@link com.causa.rca.model.artifact.CollectedArtifacts#toLlmContext()}.
 * This context contains only pre-processed summaries — never raw log lines,
 * raw event dumps, or full JFR reports.</p>
 *
 * <p>This is the final step in the RCA pipeline.</p>
 *
 * @see AnomalyDetector
 * @see RootCauseAnalyst
 * @see RcaReport
 * @see com.causa.rca.model.artifact.CollectedArtifacts
 */
@RegisterAiService(modelName = "validator")
public interface ValidationAgent {

    /**
     * Validates the RCA output and formats it into a structured {@link RcaReport}.
     *
     * <p>The {@code llmContext} parameter contains pre-processed, token-budgeted
     * summaries of pod status, metrics, events, and representative log lines.
     * Raw logs and raw events are <b>never</b> included.</p>
     *
     * @param rcaOutput  the root cause analysis text from {@link RootCauseAnalyst}
     * @param llmContext the compact summary context from
     *                   {@link com.causa.rca.model.artifact.CollectedArtifacts#toLlmContext()}
     * @return a structured {@link RcaReport} ready for presentation
     */
    @UserMessage("""
            Role: Validation Agent and JSON Generator.
            Task: Validate RCA claims using ONLY the provided Context Summaries and format a RcaReport JSON.
            CRITICAL OUTPUT RULES: Output MUST be a single valid JSON object. NO markdown. NO tables. NO prose. NO extra text. NO missing fields
            Inputs: RCA_OUTPUT, CONTEXT_SUMMARIES
            Method: 1. Extract explicit claims from RCA_OUTPUT. 
                    2. Check each claim for direct support in CONTEXT_SUMMARIES.
                    3. Do NOT infer missing data.
                    4. If support is missing, mark validation as failed.
            Output: Return ONE valid JSON object with EXACT fields: (ALL FIELDS REQUIRED)
            {
              "title": <valid title describing the issue>,
              "issue": <what was the issue in 2-3 sentences>,
              "evidence": <what evidence we have for the issue>,
              "supportedLogs": [<what are supported logs present>],
              "validationConfidence": 0.00,
            }

        Constraints:
        - Do NOT infer data not present in CONTEXT_SUMMARIES.
        - Do NOT add fields.
        - Do NOT change JSON structure.
        - Do NOT add explanations outside JSON.

        RCA_OUTPUT:{rcaOutput}

        CONTEXT_SUMMARIES:{llmContext}

        Return ONLY the JSON object.
            """)
    RcaReport validateAndFormat(@V("rcaOutput") String rcaOutput, @V("llmContext") String llmContext);
}
