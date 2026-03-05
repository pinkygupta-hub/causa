package com.causa.rca.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public class JudgmentCall {

    public String decision;

    public double confidence;

    public String reasoning;

    public JudgmentCall() {}

    public JudgmentCall(String decision, double confidence, String reasoning) {
        this.decision = decision;
        this.confidence = confidence;
        this.reasoning = reasoning;
    }
}