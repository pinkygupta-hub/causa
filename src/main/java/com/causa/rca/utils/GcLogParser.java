package com.causa.rca.utils;

import com.causa.rca.model.artifact.jvm.gc.GcEvent;
import com.causa.rca.model.artifact.jvm.gc.GcStats;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GcLogParser {
    private static final Pattern GC_PATTERN =
            Pattern.compile("\\[(\\d+\\.\\d+)s].*Pause (Young|Full).*?\\((.*?)\\)\\s+(\\d+)M->(\\d+)M.*?(\\d+\\.?\\d*)ms");

    public static List<GcEvent> parseEvents(List<String> lines) {
        List<GcEvent> events = new ArrayList<>();

        for (String line : lines) {

            Matcher m = GC_PATTERN.matcher(line);

            if (!m.find())
                continue;

            double time = Double.parseDouble(m.group(1));
            String type = m.group(2);
            String reason = m.group(3);

            double heapBefore = Double.parseDouble(m.group(4));
            double heapAfter = Double.parseDouble(m.group(5));
            double pause = Double.parseDouble(m.group(6));

            double reduction = heapBefore - heapAfter;
            double ratio = (reduction / heapBefore) * 100;

            events.add(
                    new GcEvent(
                            time,
                            type,
                            reason,
                            pause,
                            reduction,
                            ratio,
                            line
                    )
            );
        }
        return events;
    }

    private static double percentile(List<Double> sorted, int p) {

        int index =
                (int) Math.ceil((p / 100.0) * sorted.size()) - 1;

        return sorted.get(Math.max(index, 0));
    }

    public static String buildStatsReport(String label, GcStats gcStats, boolean printOutput) {

        StringBuilder sb = new StringBuilder();

        if (gcStats.events.isEmpty()) {

            sb.append("Garbage Collection Type: ")
                    .append(label)
                    .append("\n");

            sb.append("Number of Garbage Collection Events: 0\n");

            String result = sb.toString();

            if (printOutput) {
                System.out.println(result);
            }

            return result;
        }

        Collections.sort(gcStats.pauses);
        Collections.sort(gcStats.heapReductions);

        double averagePauseTime =
                gcStats.pauses.stream().mapToDouble(d -> d).average().orElse(0);

        double averageHeapReduction =
                gcStats.heapReductions.stream().mapToDouble(d -> d).average().orElse(0);

        double averageReclaimRatio =
                gcStats.reclaimRatios.stream().mapToDouble(d -> d).average().orElse(0);

        sb.append("Garbage Collection Type: ")
                .append(label)
                .append("\n\n");

        sb.append("Number of Garbage Collection Events: ")
                .append(gcStats.events.size())
                .append("\n\n");

        sb.append("Garbage Collection Trigger Reasons\n\n");

        for (Map.Entry<String, Integer> entry : gcStats.reasonCounts.entrySet()) {

            sb.append("Reason: ")
                    .append(entry.getKey())
                    .append(" occurred ")
                    .append(entry.getValue())
                    .append(" times\n");
        }

        sb.append("\n");

        sb.append("Garbage Collection Pause Time Statistics in Milliseconds\n\n");

        sb.append("Average Pause Time in Milliseconds: ")
                .append(averagePauseTime)
                .append("\n");

        sb.append("Maximum Pause Time in Milliseconds: ")
                .append(gcStats.pauses.get(gcStats.pauses.size() - 1))
                .append("\n");

        sb.append("Fiftieth Percentile Pause Time in Milliseconds (50th percentile): ")
                .append(percentile(gcStats.pauses, 50))
                .append("\n");

        sb.append("Ninetieth Percentile Pause Time in Milliseconds (90th percentile): ")
                .append(percentile(gcStats.pauses, 90))
                .append("\n");

        sb.append("Ninety Fifth Percentile Pause Time in Milliseconds (95th percentile): ")
                .append(percentile(gcStats.pauses, 95))
                .append("\n");

        sb.append("Ninety Ninth Percentile Pause Time in Milliseconds (99th percentile): ")
                .append(percentile(gcStats.pauses, 99))
                .append("\n\n");

        sb.append("Heap Memory Reclaimed During Garbage Collection in Megabytes\n\n");

        sb.append("Average Heap Memory Reclaimed in Megabytes: ")
                .append(averageHeapReduction)
                .append("\n");

        sb.append("Fiftieth Percentile Heap Memory Reclaimed in Megabytes (50th percentile): ")
                .append(percentile(gcStats.heapReductions, 50))
                .append("\n");

        sb.append("Ninetieth Percentile Heap Memory Reclaimed in Megabytes (90th percentile): ")
                .append(percentile(gcStats.heapReductions, 90))
                .append("\n");

        sb.append("Ninety Fifth Percentile Heap Memory Reclaimed in Megabytes (95th percentile): ")
                .append(percentile(gcStats.heapReductions, 95))
                .append("\n");

        sb.append("Ninety Ninth Percentile Heap Memory Reclaimed in Megabytes (99th percentile): ")
                .append(percentile(gcStats.heapReductions, 99))
                .append("\n\n");

        sb.append("Percentage of Heap Memory Reclaimed During Garbage Collection\n\n");

        sb.append("Average Percentage of Heap Memory Reclaimed: ")
                .append(averageReclaimRatio)
                .append("\n\n");

        String result = sb.toString();

        if (printOutput) {
            System.out.println(result);
        }

        return result;
    }

    public static List<String> selectDistributedLines(List<GcEvent> events,
                                                      int maxLines) {
        if (events.isEmpty())
            return List.of();

        events.sort(Comparator.comparingDouble(e -> e.time));
        int step = Math.max(1, events.size() / maxLines);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < events.size(); i += step) {
            result.add(events.get(i).rawLine);
            if (result.size() >= maxLines)
                break;
        }
        return result;
    }
}