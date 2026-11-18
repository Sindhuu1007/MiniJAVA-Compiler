package visitor;
import syntaxtree.*;
import java.util.*;

public class LSA{
    Map<String,ProcInfo> procTable;
    public Map<String,Allocation> allocs = null;
    public int maxCallArgs = 0;

    public static class Interval{
        public int start,end,temp;
        public Interval(int s,int e,int t){
            this.start = s;
            this.end = e;
            this.temp = t;
        }
        public String toString(){
            return "TEMP " + temp + ": [" + start + ", " + end + "]";
        }
    }

    private final List<String> free = new ArrayList<>();
    public LSA(Map<String,ProcInfo> procTable){
        this.procTable = procTable;
        //t0..t9, s0..s6
        for(int i = 0;i <= 9;i++){
            free.add("t"+i);
        }
        for(int i = 0; i < 7; i++){
            free.add("s"+i);
        }
    }

    public static class Allocation{
        public Map<Integer,String> allocMap;
        public Map<Integer,Integer> spillMap;
        public int stackSlotsNeeded = 0;
        Allocation(){
            this.allocMap = new HashMap<>();
            this.spillMap = new HashMap<>();
            this.stackSlotsNeeded = 0;
        }
    }

    public Map<String,Allocation> allocate(){
        Map<String,Allocation> result = new HashMap<>();
        for(String p : procTable.keySet()){
            ProcInfo proc = procTable.get(p);
            Allocation alloc = new Allocation();
            List<Interval> intervals = new ArrayList<>();
            //intervals add cheyadam
            for(Integer temp : proc.liveRanges.keySet()){
                List<Pair<Integer,Integer>> ranges = proc.liveRanges.get(temp);
                if(ranges == null) continue;
                for(Pair<Integer,Integer> r : ranges){
                    intervals.add(new Interval(r.first(),r.second(),temp));
                }
            }
            //sort by inc start
            intervals.sort(Comparator.comparingInt(i -> i.start));

            List<Interval> active = new ArrayList<>();
            Map<Interval,String> regOf = new HashMap<>();
            List<String> freeRegs = new ArrayList<>(free);

            //expiring old intervals
            for(Interval cur : intervals){
                while(!active.isEmpty() && active.get(0).end < cur.start){
                    Interval expired = active.remove(0);
                    String r = regOf.get(expired);
                    regOf.remove(expired);
                    if(r != null)freeRegs.add(r);
                }

                if(freeRegs.size() > 0){
                    //can allocate
                    String r = freeRegs.remove(0);
                    active.add(cur);
                    regOf.put(cur,r);
                    active.sort(Comparator.comparingInt(i -> i.end));
                    if(!alloc.spillMap.containsKey(cur.temp)){
                        alloc.allocMap.put(cur.temp,r);
                    }
                } 
                else{
                    // spill
                    Interval spill = active.get(active.size() - 1);
                    if(spill != null && spill.end > cur.end){
                        String r = regOf.get(spill);
                        if(!alloc.spillMap.containsKey(spill.temp)){
                            alloc.spillMap.put(spill.temp, alloc.stackSlotsNeeded);
                            alloc.stackSlotsNeeded++;
                        }
                        alloc.allocMap.remove(spill.temp);
                        active.remove(active.size() - 1);
                        regOf.remove(spill);
                        active.add(cur);
                        regOf.put(cur,r);
                        active.sort(Comparator.comparingInt(i -> i.end));
                        if(!alloc.spillMap.containsKey(cur.temp)){
                            alloc.allocMap.put(cur.temp, r);
                        }
                    }
                    else{
                        alloc.allocMap.remove(cur.temp);
                        if(!alloc.spillMap.containsKey(cur.temp)){
                            alloc.spillMap.put(cur.temp,alloc.stackSlotsNeeded);
                            alloc.stackSlotsNeeded++;
                        }
                    }
                }
            }
            result.put(p,alloc);
        }
        // store allocations for external use (P5 expects lsa.allocs)
        this.allocs = result;
        this.maxCallArgs = 0;
        return result;
    }

    // Convenience accessor used by P5
    public Map<String,Allocation> getAllocations(){
        return allocate();
    }

    public void print(){
        for(String p : procTable.keySet()){
            System.out.println("Procedure: "+p);
            Allocation alloc = allocate().get(p);
            System.out.println("Register Allocations:");
            for(Integer temp : alloc.allocMap.keySet()){
                System.out.println("TEMP "+temp+" -> "+alloc.allocMap.get(temp));
            }
           System.out.println("Spilled Temps:");
            for(Integer temp : alloc.spillMap.keySet()){
                System.out.println("TEMP "+temp+" -> stack slot "+alloc.spillMap.get(temp));
            }
            System.out.println("Total stack slots needed: "+alloc.stackSlotsNeeded);
        }
    }
}   