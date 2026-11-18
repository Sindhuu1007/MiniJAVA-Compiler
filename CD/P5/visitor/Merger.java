package visitor;
import syntaxtree.*;
import java.util.*;

/**
 * Merges all live ranges of each temporary into a single continuous interval
 * spanning from the minimum start to the maximum end index.
 * Example:
 *    [0,5], [8,9], [15,20]  →  [0,20]
 */
public class Merger {
    public Map<String, ProcInfo> procTable;

    public Merger(Map<String, ProcInfo> procTable) {
        this.procTable = procTable;
    }

    public void mergeIntervals() {
        for (String procName : procTable.keySet()) {
            ProcInfo proc = procTable.get(procName);
            if (proc.liveRanges == null) continue;

            for (int temp : proc.liveRanges.keySet()) {
                List<Pair<Integer, Integer>> ranges = proc.liveRanges.get(temp);
                if (ranges == null || ranges.isEmpty()) continue;

                int minStart = Integer.MAX_VALUE;
                int maxEnd = Integer.MIN_VALUE;

                for (Pair<Integer, Integer> r : ranges) {
                    minStart = Math.min(minStart, r.first());
                    maxEnd = Math.max(maxEnd, r.second());
                }

                // Replace with one merged interval
                List<Pair<Integer, Integer>> merged = new ArrayList<>();
                merged.add(new Pair<>(minStart, maxEnd));
                proc.liveRanges.put(temp, merged);
            }
        }
    }

    public void print() {
        for (String name : procTable.keySet()) {
            System.out.println("Procedure: " + name);
            ProcInfo p = procTable.get(name);
            if (p.liveRanges == null) continue;

            for (Integer temp : p.liveRanges.keySet()) {
                List<Pair<Integer, Integer>> ranges = p.liveRanges.get(temp);
                if (ranges == null) continue;

                for (Pair<Integer, Integer> r : ranges) {
                    System.out.println("TEMP " + temp + ": [" + r.first() + ", " + r.second() + "]");
                }
            }
        }
    }
}
