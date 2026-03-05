package com.causa.rca.ai;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(modelName = "validator")
public interface AssertionValidatorAgent {

    @UserMessage("""
You MUST return ONLY valid JSON.

If you output anything outside JSON the response will be discarded.
Do not write explanations before or after JSON.

TASK
Validate whether the ASSERTION is supported by log lines in CONTEXT_SUMMARIES.

RULES
1. Extract EXACT log lines from CONTEXT_SUMMARIES that support the ASSERTION.
2. matchedLogs must contain the exact log text copied from CONTEXT_SUMMARIES.
   matchedLogs MUST contain exact log lines.
               If log line contains quotes, remove the quotes.
               Do not escape characters.
3. If NO log line supports the assertion → matchedLogs MUST be [].
4. judgementCall MUST follow this rule:

   matchedLogs.length > 0 → "Supported"
   matchedLogs.length == 0 → "Unsupported"

5. NEVER return "Supported" if matchedLogs is empty.
6. Do NOT invent or paraphrase logs.

OUTPUT JSON FORMAT

{
  "matchedLogs": [],
  "modelAnalysisQues": [
    "Do logs show evidence supporting this assertion?",
    "Does the error occur before the failure event?",
    "Is the component mentioned in logs related to the assertion?"
  ],
  "judgementCall": "Supported",
  "confidence": 0.0,
  "reasoning": "short technical explanation"
}

EXAMPLE OUTPUT

{
  "matchedLogs": [
    "Operation cannot be fulfilled on certificates.cert-manager.io \"cert-manager-ingress-cert\": the object has been modified; please apply your changes to the latest version and try again"
  ],
  "modelAnalysisQues": [
    "Do logs show evidence supporting this assertion?",
    "Does the error occur before the failure event?",
    "Is the component mentioned in logs related to the assertion?"
  ],
  "judgementCall": "Supported",
  "confidence": 0.85,
  "reasoning": "Log shows optimistic locking preventing certificate updates."
}

ASSERTION:
{assertion}

CONTEXT_SUMMARIES:
{llmContext}
""")
    String validate(
            @V("assertion") String assertion,
            @V("llmContext") String llmContext
    );
}