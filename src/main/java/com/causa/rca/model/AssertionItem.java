package com.causa.rca.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssertionItem {

    public String assertion;

    public List<String> matchedLogs;

    public List<String> modelAnalysisQuestions;

    public JudgmentCall judgmentCall;

    public AssertionItem() {}

    public AssertionItem(String assertion,
                         List<String> matchedLogs,
                         List<String> modelAnalysisQuestions,
                         JudgmentCall judgmentCall) {
        this.assertion = assertion;
        this.matchedLogs = matchedLogs;
        this.modelAnalysisQuestions = modelAnalysisQuestions;
        this.judgmentCall = judgmentCall;
    }

    @Override
    public String toString() {
        return "[" + assertion + "] → " +
                judgmentCall.decision + " (" +
                (int)(judgmentCall.confidence * 100) + "%)";
    }
}