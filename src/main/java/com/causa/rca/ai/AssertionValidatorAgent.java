package com.causa.rca.ai;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(modelName = "validator")
public interface AssertionValidatorAgent {

    @UserMessage("""
Return ONLY valid JSON. No explanation or extra text.

TASK
Evaluate whether ASSERTION is supported by the provided MATCHED_LOGS.

IMPORTANT
MATCHED_LOGS were already selected as potential evidence.
Your job is ONLY to evaluate them.

════════ JUDGEMENT RULES ════════

Supported
→ logs clearly confirm the assertion.

Partially Supported
→ logs relate to the failure but do not fully prove the assertion.

Unsupported
→ MATCHED_LOGS is empty or unrelated to the assertion.

NEVER return Supported with empty matchedLogs.

════════ CONFIDENCE ════════

0.9–1.0  Direct log evidence  
0.7–0.8  Clear semantic implication  
0.5–0.6  Partial evidence  
0.2–0.4  Unsupported

════════ MODEL ANALYSIS QUESTIONS ════════

Return 2–3 questions challenging the assertion using the logs.

Questions must:
- reference the logs
- challenge the RCA reasoning
- not be generic

Example:
"Do logs show OOMKilled before the container restart?"

════════ OUTPUT FORMAT ════════

{
  "matchedLogs": [],
  "matchType": "direct|indirect|partial|none",
  "modelAnalysisQuestions": [],
  "judgementCall": "Supported|Partially Supported|Unsupported",
  "confidence": 0.0,
  "reasoning": "1–2 sentence explanation referencing the logs"
}

ASSERTION:
{assertion}

MATCHED_LOGS:
{llmContext}
""")
    String validate(
            @V("assertion") String assertion,
            @V("llmContext") String llmContext
    );
}