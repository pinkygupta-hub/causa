package com.causa.rca.ai;

import com.causa.rca.model.RcaReport;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * AI service interface for validating and formatting RCA analysis results.
 * <p>
 * This service performs the final step in the RCA pipeline by critiquing the root cause
 * analysis output and formatting it into a structured {@link RcaReport} object. It ensures
 * the analysis is comprehensive, logical, and properly formatted for consumption.
 * </p>
 * <p>
 * The validation includes reviewing the proposed fix for feasibility and completeness,
 * then structuring all the information (issue, evidence, logs, solution, confidence)
 * into a standardized report format.
 * </p>
 *
 * @see AnomalyDetector
 * @see RootCauseAnalyst
 * @see RcaReport
 */
@RegisterAiService(modelName = "validator")
public interface ValidationAgent {

    /**
     * Validates the RCA output and formats it into a structured report.
     * <p>
     * Reviews the root cause analysis for quality and completeness, provides a critique
     * of the proposed solution, and then formats all information into a structured
     * {@link RcaReport} object with proper fields including title, issue description,
     * evidence, supporting logs, proposed solution, and validation confidence score.
     * </p>
     *
     * @param rcaOutput the raw root cause analysis output from the {@link RootCauseAnalyst}
     *                  containing the detailed analysis and proposed fix
     * @param fullContext the complete original context data including pod status, events,
     *                    metrics, logs, and JFR analysis used for validation
     * @return a structured {@link RcaReport} object containing the validated and formatted
     *         RCA information ready for presentation
     */
    @UserMessage("""
      You are the Validation Agent. Your task is to validate the RCA output and structure it into a precise RcaReport JSON object.  

      You MUST return a valid JSON object with these EXACT fields:

      {
        "title": "Brief title summarizing the issue (e.g., 'OOM Killed - Memory Limit Exceeded')",
        "issue": "Detailed description of what went wrong and why",
        "evidence": "Key metrics, observations, and data points supporting the diagnosis",
        "supportedLogs": ["Array of relevant log entries or patterns"],
        "proposedSolution": "Concrete, actionable steps to fix the issue",
        "validationConfidence": 0.00
      }

      IMPORTANT INSTRUCTIONS:
    - Extract the issue description directly from the RCA output.
    - Include specific metrics, values, and observations in the 'evidence' field.
    - Include relevant log entries or patterns in 'supportedLogs'.
    - Proposed solutions must be actionable and directly address the root cause.
    - Set 'validationConfidence' between 0.0 and 1.0 based on your certainty in the RCA.
    - If any field is missing in the RCA, infer it logically from the provided full context.
    - Return ONLY the JSON object; do NOT include any explanatory text or commentary.

    RCA Output to Validate:  
    {rcaOutput}  

    Original Context:  
    {fullContext}
    """)
    RcaReport validateAndFormat(@V("rcaOutput") String rcaOutput, @V("fullContext") String fullContext);
}
