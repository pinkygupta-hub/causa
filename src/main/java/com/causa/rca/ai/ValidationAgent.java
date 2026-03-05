package com.causa.rca.ai;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(modelName = "validator")
public interface ValidationAgent {


    @UserMessage("""
Return ONLY valid JSON.
No explanation outside JSON.
Do NOT add or rename fields.

VALIDATION WORKFLOW:

1. Identify the ISSUE from RCA_OUTPUT.
2. If the issue contains multiple causes joined by "and", split them into separate assertions.
3. For each assertion:
   - Extract exact supporting log lines from CONTEXT_SUMMARIES.
   - Ask targeted validation checks such as:
       "Do logs confirm this cause before failure?"
       "Is there direct evidence supporting this condition?"
   - Assign decision:
       Supported if log evidence exists.
       Unsupported if no log evidence exists.
4. Aggregate finalDecision:
       If all Supported → Fully Supported.
       Otherwise → Unsupported.

RULES:
- "analysisSummary" = short summary of validation logic.
- "modelChecks" = targeted validation questions asked.
- "supportedLogs" = array of STRINGS only.
- Inside "assertions":
    - "assertion" = STRING
    - "evidence" = array of STRINGS only
    - "judgmentCall" = { decision, confidence }
    - "judgmentExplanation" = short technical reasoning
- No nested objects.
- No fields like "type" or "logLine".

Allowed decision values:
Supported
Unsupported

Allowed finalDecision.status values:
Fully Supported
Unsupported

Schema:

{
  "title": "",
  "issue": "",
  "analysisSummary": "",
  "modelChecks": [],
  "supportedLogs": [],
  "assertions": [
    {
      "assertion": "",
      "evidence": [],
      "judgmentCall": {
        "decision": "",
        "confidence": 0.0
      },
      "judgmentExplanation": ""
    }
  ],
  "finalDecision": {
    "status": ""
  }
}

RCA_OUTPUT:
{rcaOutput}

CONTEXT_SUMMARIES:
{llmContext}
""")
        String validateAndFormat(
                @V("rcaOutput") String rcaOutput,
                @V("llmContext") String llmContext
        );
    }
