package com.causa.rca.model.artifact.jvm.gc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GcStats {
    public List<Double> pauses = new ArrayList<>();
    public List<Double> heapReductions = new ArrayList<>();
    public List<Double> reclaimRatios = new ArrayList<>();
    public List<GcEvent> events = new ArrayList<>();

    public Map<String,Integer> reasonCounts = new HashMap<>();

    public void add(GcEvent e) {

        pauses.add(e.pause);
        heapReductions.add(e.heapReduction);
        reclaimRatios.add(e.reclaimRatio);
        events.add(e);
        reasonCounts.merge(e.reason,1,Integer::sum);
    }
}