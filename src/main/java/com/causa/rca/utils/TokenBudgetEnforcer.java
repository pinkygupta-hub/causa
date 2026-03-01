package com.causa.rca.utils;

import com.causa.rca.model.artifact.CollectedArtifacts;
import com.causa.rca.model.artifact.LogArtifact;
import com.causa.rca.model.artifact.LogArtifact.LogSummary;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Utility that counts tokens in the LLM context and enforces a hard cap of 4 000 tokens.
 *
 * <h3>Token counting</h3>
 * Uses <a href="https://github.com/knuddelsgmbh/jtokkit">jtokkit</a> with the
 * {@code cl100k_base} encoding (GPT-4 / GPT-3.5-turbo compatible).  This gives
 * accurate token counts before any LLM call.
 *
 * <h3>Truncation strategy (when token count > 4 000)</h3>
 * <ol>
 *   <li>Metrics and pod status are <b>never</b> truncated.</li>
 *   <li>Events are <b>never</b> truncated (only the summary is sent to LLMs).</li>
 *   <li>Log representative lines are truncated first:
 *       errors/warnings are preserved; info lines are dropped until budget fits.</li>
 *   <li>If still over budget, representative lines are trimmed from the bottom.</li>
 * </ol>
 *
 * <p>After enforcement, {@link CollectedArtifacts#tokenCount} and
 * {@link CollectedArtifacts#truncationApplied} are populated.</p>
 */
@ApplicationScoped
public class TokenBudgetEnforcer {

    private static final Logger LOG = Logger.getLogger(TokenBudgetEnforcer.class);

    /** Hard token cap for the combined LLM context. */
    public static final int TOKEN_HARD_CAP = 4_000;

    private final Encoding encoding;

    public TokenBudgetEnforcer() {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        // cl100k_base is used by GPT-4 and GPT-3.5-turbo; a good proxy for most LLMs
        this.encoding = registry.getEncoding(EncodingType.CL100K_BASE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Counts tokens in the LLM context derived from {@code artifacts} and, if the
     * count exceeds {@link #TOKEN_HARD_CAP}, truncates log representative lines
     * until the budget is satisfied.
     *
     * <p>Mutates {@code artifacts.logs.summary.representativeLines},
     * {@code artifacts.tokenCount}, and {@code artifacts.truncationApplied} in-place.</p>
     *
     * @param artifacts the collected artifacts to enforce the budget on
     */
    public void enforce(CollectedArtifacts artifacts) {
        if (artifacts == null) return;

        String context = artifacts.toLlmContext();
        int tokenCount = countTokens(context);

        LOG.info("Token count before enforcement: " + tokenCount + " / " + TOKEN_HARD_CAP);

        if (tokenCount <= TOKEN_HARD_CAP) {
            artifacts.tokenCount       = tokenCount;
            artifacts.truncationApplied = false;
            return;
        }

        // Need to truncate – only log representative lines are eligible
        LOG.warn("Token budget exceeded (" + tokenCount + " > " + TOKEN_HARD_CAP
                + "). Truncating log representative lines...");

        boolean truncated = truncateLogs(artifacts);
        artifacts.truncationApplied = truncated;

        // Recount after truncation
        artifacts.tokenCount = countTokens(artifacts.toLlmContext());
        LOG.info("Token count after enforcement: " + artifacts.tokenCount
                + " (truncation applied: " + artifacts.truncationApplied + ")");
    }

    /**
     * Counts the number of tokens in the given text using {@code cl100k_base} encoding.
     *
     * @param text the text to count tokens for
     * @return the token count
     */
    public int countTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        return encoding.countTokens(text);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Truncation logic
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Truncates log representative lines to bring the context within the token budget.
     *
     * <p>Strategy:
     * <ol>
     *   <li>First pass: remove INFO lines (lowest priority) from the bottom.</li>
     *   <li>Second pass: if still over budget, remove WARN lines from the bottom.</li>
     *   <li>Third pass: if still over budget, remove ERROR lines from the bottom
     *       (last resort – at least one error line is always preserved).</li>
     * </ol>
     * </p>
     *
     * @param artifacts the artifacts to truncate
     * @return {@code true} if any truncation was applied
     */
    private boolean truncateLogs(CollectedArtifacts artifacts) {
        if (artifacts.logs == null || artifacts.logs.summary == null) return false;

        LogSummary summary = artifacts.logs.summary;
        if (summary.representativeLines == null || summary.representativeLines.isEmpty()) {
            return false;
        }

        List<String> lines = new ArrayList<>(summary.representativeLines);
        boolean anyTruncated = false;

        // Pass 1: drop INFO lines from the bottom
        anyTruncated |= dropLinesByPriority(lines, "info", artifacts, false);

        // Pass 2: drop WARN lines from the bottom (if still over budget)
        if (countTokens(artifacts.toLlmContext()) > TOKEN_HARD_CAP) {
            anyTruncated |= dropLinesByPriority(lines, "warn", artifacts, false);
        }

        // Pass 3: drop ERROR lines from the bottom, but keep at least 1
        if (countTokens(artifacts.toLlmContext()) > TOKEN_HARD_CAP) {
            anyTruncated |= dropLinesByPriority(lines, "error", artifacts, true);
        }

        // Final safety: if still over budget, hard-trim from the bottom
        if (countTokens(artifacts.toLlmContext()) > TOKEN_HARD_CAP) {
            while (lines.size() > 1 && countTokens(artifacts.toLlmContext()) > TOKEN_HARD_CAP) {
                lines.remove(lines.size() - 1);
                summary.representativeLines = new ArrayList<>(lines);
                anyTruncated = true;
            }
        }

        return anyTruncated;
    }

    /**
     * Removes lines of the given priority level from the bottom of the list until
     * the token budget is satisfied or no more lines of that priority remain.
     *
     * @param lines      the mutable list of representative lines (modified in-place)
     * @param priority   "info", "warn", or "error" (case-insensitive)
     * @param artifacts  the artifacts (used to recompute context after each removal)
     * @param keepOne    if {@code true}, always keep at least one line of this priority
     * @return {@code true} if any lines were removed
     */
    private boolean dropLinesByPriority(List<String> lines, String priority,
                                         CollectedArtifacts artifacts, boolean keepOne) {
        boolean removed = false;
        int minKeep = keepOne ? 1 : 0;

        // Count how many lines of this priority exist
        long priorityCount = lines.stream()
                .filter(l -> matchesPriority(l, priority))
                .count();

        // Iterate from the bottom, removing matching lines
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (countTokens(artifacts.toLlmContext()) <= TOKEN_HARD_CAP) break;
            if (priorityCount <= minKeep) break;

            String line = lines.get(i);
            if (matchesPriority(line, priority)) {
                lines.remove(i);
                artifacts.logs.summary.representativeLines = new ArrayList<>(lines);
                priorityCount--;
                removed = true;
            }
        }
        return removed;
    }

    private boolean matchesPriority(String line, String priority) {
        String upper = line.toUpperCase(Locale.ROOT);
        return switch (priority.toLowerCase(Locale.ROOT)) {
            case "error" -> upper.contains("ERROR") || upper.contains("SEVERE") || upper.contains("FATAL");
            case "warn"  -> upper.contains("WARN") || upper.contains("WARNING");
            case "info"  -> upper.contains("INFO") && !upper.contains("ERROR")
                                && !upper.contains("WARN") && !upper.contains("SEVERE");
            default      -> false;
        };
    }
}

// Made with Bob
