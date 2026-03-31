package com.causa.rca.ai;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(modelName = "validator")
public interface RcaAssertionExtractor {

    @UserMessage("""
Return ONLY valid JSON. No explanation, no markdown.
Stop immediately after the closing }.

TASK
Extract the ISSUE and ASSERTIONS from the ROOT_CAUSE text.

ISSUE RULE

If the input contains:

ROOT_CAUSE: <text>

Extract the COMPLETE ROOT_CAUSE text exactly as provided.

- Include ALL sentences (multi-line if present)
- Do NOT truncate to first sentence
- Do NOT summarize
- Do NOT rewrite
- Preserve original wording exactly

The issueIdentified must contain the FULL paragraph after ROOT_CAUSE.

ASSERTION RULES

Assertions must be SMALLER pieces of the ROOT_CAUSE text (may span multiple sentences).

Important constraints:

- Assertions MUST reuse the same keywords that appear in the ROOT_CAUSE.
- Do NOT introduce new terminology.
- Do NOT invent new causes.
- Do NOT change wording significantly.

Assertions should simply break the root cause into
short verifiable statements using the SAME words.

GOOD EXAMPLE

ROOT_CAUSE:
"The container heap-oom-prom was killed due to an out-of-memory error."

GOOD ASSERTIONS:
"Container was killed due to out-of-memory"
"Container terminated with OOM error"

BAD ASSERTIONS:
"Application crash occurred"
"System instability detected"
"Hardware failure happened"

Generate 2–3 assertions maximum.

OUTPUT FORMAT
{
  "issueIdentified": "",
  "assertions": []
}

Rules:
- assertions must be an array of strings
- assertions must reuse keywords from issueIdentified
- no nested objects

INPUT
ROOT_CAUSE_TEXT:
{rcaOutput}
""")
    String extract(@V("rcaOutput") String rcaOutput);
}