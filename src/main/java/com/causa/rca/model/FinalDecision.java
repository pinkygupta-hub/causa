package com.causa.rca.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public class FinalDecision {

    public String status;

    public String summary;

    public FinalDecision() {}

    public FinalDecision(String status, String summary) {
        this.status = status;
        this.summary = summary;
    }
}