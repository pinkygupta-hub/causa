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
            Role: Executive Root Cause Analysis Validator and JSON Generator.
            AUDIENCE: Senior leadership. Assume the reader is NOT deeply technical.
            Task: Validate RCA claims using ONLY the provided Context Summaries and format a RcaReport JSON.
            CRITICAL OUTPUT RULES: Output MUST be a single valid JSON object. NO missing fields
            Inputs: RCA_OUTPUT:{rcaOutput}, CONTEXT_SUMMARIES:{llmContext}
            Method: 1. Extract explicit claims from RCA_OUTPUT. 
                    2. Check each claim for direct support in CONTEXT_SUMMARIES.
                    3. Do NOT infer missing data.
                    4. If support is missing, mark validation as failed.
                    5. DO NOT QUOTE ANYTHING ON CPU USAGE for OOM_KILLED ISSUE 
            Output: Return ONE valid JSON object with EXACT fields: (ALL FIELDS REQUIRED)
            OUTPUT JSON SCHEMA: (ALL FIELDS REQUIRED)
            {
              "title": "Short, clear title describing the primary issue",
              "issue": "Generate a very detailed explanation of what went wrong and how it occurred. Explain what went wrong and how it happened in plain English. Reference ROOT_CAUSE from RCA_OUTPUT. Describe impact as if explaining to leadership.",
              "evidence": "Summarize key supporting evidence in human-readable form. Reference KEY_EVIDENCE from RCA_OUTPUT.",
              "supportedLogs": "Include direct, verbatim log lines from CONTEXT_SUMMARIES that support the issue.",
              "validationConfidence": 0.00,
              "validationSummary": "Generate an overall assessment of RCA validity in 2–3 lines.",
              "validationTrace": [
                {
                  "claim": "Extract a specific RCA claim describing the issue",
                  "expectedEvidence": "Describe what evidence should exist to prove this claim",
                  "searchScope": "Describe where the evidence was searched (e.g., logs, metrics, events)",
                  "evidenceFound": [
                    "Include verbatim evidence lines found in CONTEXT_SUMMARIES or RCA_OUTPUT"
                  ],
                  "comparisonLogic": "Explain how the evidence was evaluated against the claim",
                  "decision": "Supported | Weakly Supported | Unsupported",
                  "confidence": 0.00,
                  "notes": "Describe limitations, risks, or uncertainty in the validation"
                }
              ]
            }

        Constraints:
        - Do NOT add fields.
        - Do NOT change JSON structure.
        Return ONLY the JSON object.
            """)
    RcaReport validateAndFormat(@V("rcaOutput") String rcaOutput, @V("llmContext") String llmContext);
}
