package com.causa.rca.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

@RegisterForReflection
public class RcaReport {

    // Box formatting constants
    private static final int BOX_TOTAL_WIDTH = 86;
    private static final int BOX_CONTENT_WIDTH = BOX_TOTAL_WIDTH - 2;
    private static final int TITLE_MAX_LENGTH = 76;
    private static final int MAX_WORD_LENGTH = BOX_CONTENT_WIDTH - 2;

    public String title;
    public String issue;
    public String evidence;
    public List<String> supportedLogs;
    public List<String> validationChecks;
    public List<AssertionItem> assertions;
    public FinalDecision finalDecision;

    public RcaReport() {}

    public RcaReport(String title,
                     String issue,
                     String evidence,
                     List<String> supportedLogs,
                     List<AssertionItem> assertions,
                     List<String> validationChecks,
                     FinalDecision finalDecision) {
        this.title = title;
        this.issue = issue;
        this.evidence = evidence;
        this.supportedLogs = supportedLogs;
        this.validationChecks = validationChecks;
        this.assertions = assertions;
        this.finalDecision = finalDecision;
    }

    // Convenience constructor
    public RcaReport(String title,
                     String issue,
                     String evidence,
                     List<String> supportedLogs) {
        this(title, issue, evidence, supportedLogs, null, null, null);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("\n╔════════════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                           RCA REPORT                                               ║\n");
        sb.append("╠════════════════════════════════════════════════════════════════════════════════════╣\n");

        sb.append(String.format("║ Title: %-" + TITLE_MAX_LENGTH + "s║\n",
                truncate(title, TITLE_MAX_LENGTH)));

        sb.append("╠════════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append("║ Issue Description:                                                                 ║\n");
        appendWrapped(sb, issue);

        sb.append("╠════════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append("║ Evidence:                                                                          ║\n");
        appendWrapped(sb, evidence);

        if (supportedLogs != null && !supportedLogs.isEmpty()) {
            sb.append("╠════════════════════════════════════════════════════════════════════════════════════╣\n");
            sb.append("║ Supported Logs:                                                                    ║\n");
            for (String log : supportedLogs) {
                appendWrapped(sb, "  • " + log);
            }
        }

        if (validationChecks != null && !validationChecks.isEmpty()) {
            sb.append("╠════════════════════════════════════════════════════════════════════════════════════╣\n");
            sb.append("║ Assertion Challenge:                                                                    ║\n");
            for (String log : validationChecks) {
                appendWrapped(sb, "  • " + log);
            }
        }

        if (assertions != null && !assertions.isEmpty()) {
            sb.append("╠════════════════════════════════════════════════════════════════════════════════════╣\n");
            sb.append("║ Assertions & Judgment Calls:                                                       ║\n");
            for (AssertionItem item : assertions) {
                appendWrapped(sb, "  • " + item.toString());
            }
        }

        if (finalDecision != null) {
            sb.append("╠════════════════════════════════════════════════════════════════════════════════════╣\n");
            appendWrapped(sb, "Final Decision: " + finalDecision.status);
        }

        sb.append("╚════════════════════════════════════════════════════════════════════════════════════╝\n");

        return sb.toString();
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        return str.length() > maxLength
                ? str.substring(0, maxLength - 3) + "..."
                : str;
    }

    private void appendWrapped(StringBuilder sb, String text) {
        if (text == null || text.trim().isEmpty()) {
            StringBuilder naLine = new StringBuilder("║ N/A");
            while (naLine.length() < BOX_TOTAL_WIDTH - 1) naLine.append(" ");
            naLine.append("║\n");
            sb.append(naLine);
            return;
        }

        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder("║ ");

        for (String word : words) {

            if (word.length() > MAX_WORD_LENGTH) {
                while (line.length() < BOX_TOTAL_WIDTH - 1) line.append(" ");
                line.append("║\n");
                sb.append(line);
                line = new StringBuilder("║ ");
                continue;
            }

            if (line.length() + word.length() + 1 >= BOX_TOTAL_WIDTH - 1) {
                while (line.length() < BOX_TOTAL_WIDTH - 1) line.append(" ");
                line.append("║\n");
                sb.append(line);
                line = new StringBuilder("║ ");
            }

            line.append(word).append(" ");
        }

        if (line.length() > 2) {
            while (line.length() < BOX_TOTAL_WIDTH - 1) line.append(" ");
            line.append("║\n");
            sb.append(line);
        }
    }
}