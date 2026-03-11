package com.causa.rca.ai;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(modelName = "validator")
public interface AssertionValidatorAgent {

    @UserMessage("""
You MUST return ONLY valid JSON. No explanation, no markdown, no text outside JSON.

═══════════════════════════════════════════════
TASK
═══════════════════════════════════════════════
Determine whether ASSERTION is supported by evidence in CONTEXT_SUMMARIES.
Evidence may be DIRECT (exact wording) or INDIRECT (semantic implication).

═══════════════════════════════════════════════
EVIDENCE MATCHING RULES  — read carefully
═══════════════════════════════════════════════

DIRECT match (strongest):
  The log line contains the same keywords or wording as the assertion.
  Example assertion : "Container runtime network was not ready"
  Example log line  : "NetworkNotReady: container runtime network not ready"
  → This IS a direct match. Add it to matchedLogs.

INDIRECT / SEMANTIC match (also valid):
  The log line does not use the same words but strongly implies the condition.
  Examples of valid indirect matches:
    Assertion: "OOM kill occurred"
      → log: "OOMKilled" or "Killing process … out of memory" or "memory limit exceeded"
    Assertion: "Certificate renewal failed"
      → log: "failed to renew cert" or "x509: certificate has expired" or "acme: error"
    Assertion: "Controller conflict prevented update"
      → log: "leader election lost" or "resource version conflict" or "optimistic locking"
    Assertion: "Pod could not be scheduled"
      → log: "Insufficient cpu" or "0/3 nodes available" or "FailedScheduling"

  If a log line is an indirect match, STILL add it to matchedLogs.
  Copy the log line EXACTLY as it appears in CONTEXT_SUMMARIES. Do not paraphrase.

PARTIAL match (permitted when no better match exists):
  If no direct or indirect match exists, check for:
  - Any log mentioning the same COMPONENT referenced in the assertion
  - Any log mentioning the same ERROR CLASS (e.g., timeout, auth, cert, network, oom)
  If a partial match exists → add it and set judgementCall to "Partially Supported"

NO match:
  If truly no log line relates to the assertion in any way →
  matchedLogs MUST be [] and judgementCall MUST be "Unsupported"

═══════════════════════════════════════════════
JUDGEMENT RULES
═══════════════════════════════════════════════
"Supported"           → 1+ direct or indirect matched log lines
"Partially Supported" → only partial/component-level matches, or low confidence
"Unsupported"         → matchedLogs is [] after genuine search

NEVER return "Supported" with empty matchedLogs.
Only consider logs related to the same component, resource, or failure type as the assertion. If logs are unrelated, return Unsupported.

═══════════════════════════════════════════════
CONFIDENCE SCORING
═══════════════════════════════════════════════
0.9 – 1.0 : Direct keyword match in log
0.7 – 0.8 : Clear semantic / indirect match
0.5 – 0.6 : Partial or component-level match
0.2 – 0.4 : No evidence found (Unsupported)

═══════════════════════════════════════════════
modelAnalysisQuestions — return 2–3 questions
═══════════════════════════════════════════════
Questions must be:
- Specific to this assertion (not generic)
- Focused on log evidence
- Designed to challenge the assertion logically

Bad (too generic):  "Do logs contain errors?"
Good (specific):    "Do logs show a certificate expiry event before the controller restart?"

═══════════════════════════════════════════════
OUTPUT FORMAT — return exactly this schema
═══════════════════════════════════════════════
{
  "matchedLogs": [],
  "matchType": "direct|indirect|partial|none",
  "modelAnalysisQuestions": [],
  "judgementCall": "Supported|Partially Supported|Unsupported",
  "confidence": 0.0,
  "reasoning": "1–2 sentence technical explanation referencing the log evidence"
}

ASSERTION: {assertion}

CONTEXT_SUMMARIES:
{llmContext}
""")
    String validate(
            @V("assertion") String assertion,
            @V("llmContext") String llmContext
    );
}