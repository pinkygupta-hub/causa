package com.causa.rca.model.artifact.jvm.gc;

public class GcEvent {
    public double time;
    public String type;
    String reason;
    double pause;
    double heapReduction;
    double reclaimRatio;
    public String rawLine;

    public GcEvent(double time,
                   String type,
                   String reason,
                   double pause,
                   double heapReduction,
                   double reclaimRatio,
                   String rawLine) {

        this.time = time;
        this.type = type;
        this.reason = reason;
        this.pause = pause;
        this.heapReduction = heapReduction;
        this.reclaimRatio = reclaimRatio;
        this.rawLine = rawLine;
    }
}