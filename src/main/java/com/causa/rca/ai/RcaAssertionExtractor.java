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
Extract the ISSUE and generate ASSERTIONS from the RCA text.

ISSUE RULE
If the RCA text contains:
ROOT_CAUSE: <text>

Extract only the text after ROOT_CAUSE as "issueIdentified".
Do NOT rewrite or summarize it.

ASSERTION RULES
- Generate 2–3 assertions.
- Each assertion must be ONE technical claim.
- Assertions must be short and log-verifiable.
- Do not repeat the issue text.
- Avoid vague phrases like "system failed".

Good examples:
"Controller conflict prevented certificate reconciliation"
"Certificate reconciliation attempts failed"
"Certificate update operations failed in the Kubernetes control plane"

OUTPUT FORMAT
{
  "issueIdentified": "",
  "assertions": []
}

Rules:
- assertions must be an array of strings
- 2–3 assertions preferred
- no nested objects

INPUT
RCA_TEXT:
{rcaOutput}
""")
    String extract(@V("rcaOutput") String rcaOutput);
}