package com.causa.rca.ai;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(modelName = "validator")
public interface EvidenceMatcherAgent {

    @UserMessage("""
Return ONLY valid JSON.
Do NOT explain anything.
Output must start with { and end with }.

TASK
Find log lines in LOGS that support ASSERTION.

IMPORTANT RULES

1. Copy log lines EXACTLY as they appear in LOGS.
2. Do NOT invent or modify log lines.
3. Do NOT copy instructions from this prompt.
4. NEVER return lines starting with:
   - "OOM signals"
   - "Crash signals"
   - "ASSERTION"
   - "LOGS"
5. If no log line supports the assertion, return an empty array.
6. Every returned log line MUST exist verbatim in LOGS.

Evidence signals include Kubernetes failure patterns such as:
OOMKilled, exit code 137, OutOfMemoryError,
CrashLoopBackOff, BackOff restarting failed container,
or restart events.

MATCH TYPES

direct
  log contains same wording as assertion

indirect
  log implies the condition using Kubernetes signals

none
  no relevant logs found

OUTPUT FORMAT

{
 "matchedLogs": [
   "BackOff restarting failed container heap-oom-prom",
   "OOMKilled(137)"
 ],
 "matchType": "direct"
}

ASSERTION:
{assertion}

LOGS:
{logs}
""")
    String match(
            @V("assertion") String assertion,
            @V("logs") String logs
    );
}