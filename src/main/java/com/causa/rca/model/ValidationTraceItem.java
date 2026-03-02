package com.causa.rca.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;

@RegisterForReflection
public class ValidationTraceItem {

    public String claim;
    public String expectedEvidence;
    public String searchScope;
    public List<String> evidenceFound;
    public String comparisonLogic;
    public String decision;
    public Double confidence;
    public String notes;

    public ValidationTraceItem() {}

    /** Returns confidence as a percentage string, e.g. "85%" */
    public String getFormattedConfidence() {
        if (confidence == null) return "";
        return String.format("%.0f%%", confidence * 100);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (claim != null) sb.append("[").append(claim).append("] ");
        if (decision != null) sb.append("→ ").append(decision);
        if (confidence != null) sb.append(String.format(" (%.0f%%)", confidence * 100));
        if (notes != null && !notes.isBlank()) sb.append(" | ").append(notes);
        return sb.toString();
    }
}

