package com.causa.rca.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

/**
 * Data model representing a Root Cause Analysis (RCA) report.
 * <p>
 * This class encapsulates the complete results of an RCA analysis, including the identified
 * issue, supporting evidence, relevant logs, validation notes, a validation checklist,
 * and a confidence score.
 * It serves as the final output of the RCA pipeline and is returned to clients via the REST API.
 * </p>
 * <p>
 * The class is registered for reflection to support JSON serialization/deserialization
 * in native image builds and for use with AI services that generate structured outputs.
 * </p>
 *
 * @see com.causa.rca.ai.ValidationAgent
 * @see com.causa.rca.rest.RcaResource
 */
@RegisterForReflection
public class RcaReport {

    // Box formatting constants
    private static final int BOX_TOTAL_WIDTH = 86;
    private static final int BOX_CONTENT_WIDTH = BOX_TOTAL_WIDTH - 2;
    private static final int TITLE_MAX_LENGTH = 76;
    private static final int CONFIDENCE_LABEL_WIDTH = 60;
    private static final int MAX_WORD_LENGTH = BOX_CONTENT_WIDTH - 2;

    /**
     * The title or summary of the RCA report.
     * Provides a concise description of the issue being analyzed.
     */
    public String title;

    /**
     * Detailed description of the identified issue.
     * Explains what problem was detected and its impact on the system.
     */
    public String issue;

    /**
     * Evidence supporting the root cause analysis.
     * Contains metrics, observations, and data points that led to the conclusion.
     */
    public String evidence;

    /**
     * List of relevant log entries that support the analysis.
     * Contains specific log lines or patterns that are pertinent to the issue.
     */
    public List<String> supportedLogs;

    /**
     * Confidence score of the validation (0.0 to 1.0).
     * Represents how confident the validation agent is in the analysis.
     * Higher values indicate greater confidence in the RCA results.
     */
    public Double validationConfidence;

    /**
     * Free-text notes from the validation agent explaining the validation outcome.
     * Describes what was verified, what was missing, and why the confidence score was assigned.
     */
    public String validationNotes;

    /**
     * Structured checklist produced by the validation agent.
     * Each entry is a "Yes/No" item indicating whether a specific claim in the RCA
     * was directly supported by the collected context summaries.
     * Example: ["Metrics confirm OOM: Yes", "Log evidence present: No"]
     */
    public List<String> validationChecklist;

    /**
     * Default constructor for JSON deserialization and reflection.
     */
    public RcaReport() {
    }

    /**
     * Constructs a complete RCA report with all fields.
     *
     * @param title               the title or summary of the report
     * @param issue               detailed description of the identified issue
     * @param evidence            supporting evidence for the root cause
     * @param supportedLogs       list of relevant log entries
     * @param validationConfidence confidence score (0.0 to 1.0)
     * @param validationNotes     free-text validation notes
     * @param validationChecklist structured Yes/No checklist items
     */
    public RcaReport(String title, String issue, String evidence,
                     List<String> supportedLogs,
                     Double validationConfidence,
                     String validationNotes,
                     List<String> validationChecklist) {
        this.title               = title;
        this.issue               = issue;
        this.evidence            = evidence;
        this.supportedLogs       = supportedLogs;
        this.validationConfidence = validationConfidence;
        this.validationNotes     = validationNotes;
        this.validationChecklist = validationChecklist;
    }

    /**
     * Convenience constructor for simple healthy/error reports that don't need
     * validation notes or checklist.
     *
     * @param title               the title
     * @param issue               the issue description
     * @param evidence            the evidence
     * @param supportedLogs       supported log entries
     * @param validationConfidence confidence score
     */
    public RcaReport(String title, String issue, String evidence,
                     List<String> supportedLogs,
                     Double validationConfidence) {
        this(title, issue, evidence, supportedLogs, validationConfidence, null, null);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔════════════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                           RCA REPORT                                               ║\n");
        sb.append("╠════════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ Title: %-" + TITLE_MAX_LENGTH + "s║\n", truncate(title, TITLE_MAX_LENGTH)));
        sb.append("╠════════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append("║ Issue Description:                                                                 ║\n");
        appendWrapped(sb, issue, BOX_TOTAL_WIDTH);
        sb.append("╠════════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append("║ Evidence:                                                                          ║\n");
        appendWrapped(sb, evidence, BOX_TOTAL_WIDTH);
        if (supportedLogs != null && !supportedLogs.isEmpty()) {
            sb.append("╠════════════════════════════════════════════════════════════════════════════════════╣\n");
            sb.append("║ Supported Logs:                                                                    ║\n");
            for (String log : supportedLogs) {
                appendWrapped(sb, "  • " + log, BOX_TOTAL_WIDTH);
            }
        }
        sb.append("╠════════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ Validation Confidence: %-" + CONFIDENCE_LABEL_WIDTH + ".2f║\n",
                validationConfidence != null ? validationConfidence : 0.0));
        if (validationNotes != null && !validationNotes.isBlank()) {
            sb.append("╠════════════════════════════════════════════════════════════════════════════════════╣\n");
            sb.append("║ Validation Notes:                                                                  ║\n");
            appendWrapped(sb, validationNotes, BOX_TOTAL_WIDTH);
        }
        if (validationChecklist != null && !validationChecklist.isEmpty()) {
            sb.append("╠════════════════════════════════════════════════════════════════════════════════════╣\n");
            sb.append("║ Validation Checklist:                                                              ║\n");
            for (String item : validationChecklist) {
                appendWrapped(sb, "  • " + item, BOX_TOTAL_WIDTH);
            }
        }
        sb.append("╚════════════════════════════════════════════════════════════════════════════════════╝\n");
        return sb.toString();
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        return str.length() > maxLength ? str.substring(0, maxLength - 3) + "..." : str;
    }

    private void appendWrapped(StringBuilder sb, String text, int width) {
        if (text == null || text.trim().isEmpty()) {
            StringBuilder naLine = new StringBuilder("║ N/A");
            while (naLine.length() < width - 1) naLine.append(" ");
            naLine.append("║\n");
            sb.append(naLine);
            return;
        }

        String[] words = text.split("\\s+");
        if (words.length == 0) {
            StringBuilder naLine = new StringBuilder("║ N/A");
            while (naLine.length() < width - 1) naLine.append(" ");
            naLine.append("║\n");
            sb.append(naLine);
            return;
        }

        StringBuilder line = new StringBuilder("║ ");
        for (String word : words) {
            if (word.length() > MAX_WORD_LENGTH) {
                if (line.length() > 2) {
                    while (line.length() < width - 1) line.append(" ");
                    line.append("║\n");
                    sb.append(line);
                    line = new StringBuilder("║ ");
                }
                int pos = 0;
                while (pos < word.length()) {
                    int chunkSize = Math.min(MAX_WORD_LENGTH, word.length() - pos);
                    String chunk = word.substring(pos, pos + chunkSize);
                    line.append(chunk);
                    while (line.length() < width - 1) line.append(" ");
                    line.append("║\n");
                    sb.append(line);
                    line = new StringBuilder("║ ");
                    pos += chunkSize;
                }
                continue;
            }
            if (line.length() + word.length() + 1 >= width - 1) {
                while (line.length() < width - 1) line.append(" ");
                line.append("║\n");
                sb.append(line);
                line = new StringBuilder("║ ");
            }
            line.append(word).append(" ");
        }
        if (line.length() > 2) {
            while (line.length() < width - 1) line.append(" ");
            line.append("║\n");
            sb.append(line);
        }
    }
}

