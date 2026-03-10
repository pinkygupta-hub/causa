package com.causa.rca.ai;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(modelName = "validator")
public interface RcaAssertionExtractor {

    @UserMessage("""
Return ONLY valid JSON.
No explanation.
No markdown.
No nested objects.
Do NOT repeat the input.
Stop immediately after the closing }.

═══════════════════════════════════════════════
TASK
═══════════════════════════════════════════════
Extract the ISSUE and derive ASSERTIONS from the RCA text.

═══════════════════════════════════════════════
ISSUE EXTRACTION RULE
═══════════════════════════════════════════════

If the RCA text explicitly states the root cause or issue,
the field "issueIdentified" MUST contain that exact text.

Do NOT rewrite, summarize, or reinterpret it.

If the RCA text starts with:

ROOT_CAUSE: <text>

Then extract ONLY the text after "ROOT_CAUSE:".

Example:

Input:
ROOT_CAUSE: Kubernetes control plane unable to update certificates due to controller conflict.

Output issueIdentified:
"Kubernetes control plane unable to update certificates due to controller conflict"

═══════════════════════════════════════════════
ASSERTION GENERATION RULES
═══════════════════════════════════════════════

Assertions must represent **verifiable technical claims**
that can later be validated against logs.

Rules:

1. Each assertion must represent ONE technical claim.
2. Split cause, effect, and symptoms into separate assertions.
3. Avoid long combined sentences.
4. Always produce 2–3 assertions when possible.
5. Assertions must be STRINGS only.
6. Assertions must be concise and technical.

Good examples:

"Controller conflict prevented certificate reconciliation"

"Certificate update operations failed in the Kubernetes control plane"

"TLS certificate resources could not be updated due to controller conflict"

Bad examples:

"The system failed"

"There was an error"

═══════════════════════════════════════════════
OUTPUT FORMAT
═══════════════════════════════════════════════

Return exactly this JSON structure:

{
  "issueIdentified": "",
  "assertions": []
}

Rules:

- assertions must be an array of strings
- produce between 2 and 4 assertions
- do not repeat the issue text verbatim
- do not include nested objects

═══════════════════════════════════════════════
INPUT
═══════════════════════════════════════════════

RCA_TEXT:
{rcaOutput}

""")
    String extract(@V("rcaOutput") String rcaOutput);
}