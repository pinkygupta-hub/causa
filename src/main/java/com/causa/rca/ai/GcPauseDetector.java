package com.causa.rca.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(modelName = "detector")
public interface GcPauseDetector {
    
    @SystemMessage("""
            Role: GC Pause Detection Specialist.
            Input: SUMMARIZED_LOGS (not raw logs)
            ALLOWED OUTPUT TOKENS (EXACT, CASE-SENSITIVE):
                GC_PAUSE, NO_GC_ISSUE
            CRITICAL OUTPUT RULES: 
                1. Output MUST be structured, NO markdown, NO explanations outside fields
                2. Return EXACTLY this structure:
                   ANOMALY_TYPE: <ONLY ONE token from above ALLOWED OUTPUT TOKENS>
                   EXPLANATION: <Explanation of GC behavior observed>
            Task: Analyze summarized logs for excessive GC activity patterns.
            Look for: Full GC events, long GC pauses, frequent GC cycles, GC overhead warnings.
            """)
    String detectGcPause(@UserMessage String summarizedLogs);
}
