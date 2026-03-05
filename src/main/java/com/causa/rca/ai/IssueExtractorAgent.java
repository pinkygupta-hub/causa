package com.causa.rca.ai;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(modelName = "validator")
public interface IssueExtractorAgent {

    @UserMessage("""
Return ONLY valid JSON.
No explanation.
No markdown.
No nested objects.
Do NOT repeat the input.
Stop immediately after the closing }.

Extract the main issue and break it into ATOMIC TECHNICAL ASSERTIONS.

Rules:
1. Each assertion must represent ONE verifiable technical claim.
2. Split cause, effect, and symptoms into separate assertions.
3. Do NOT keep a long combined sentence.
4. Always produce 2–4 assertions if possible.
5. Assertions must be STRINGS only.

Example:

Input:
OOM_KILLED due to container runtime network not ready and CNI configuration missing.

Output:
{
  "issueIdentified": "OOM kill occurred due to networking initialization failure.",
  "assertions": [
    "Container runtime network was not ready",
    "CNI configuration was missing",
    "OOM kill occurred after networking initialization failure"
  ]
}

Schema:
{
  "issueIdentified": "",
  "assertions": []
}

RCA_OUTPUT:
{rcaOutput}
""")
    String extract(@V("rcaOutput") String rcaOutput);
}