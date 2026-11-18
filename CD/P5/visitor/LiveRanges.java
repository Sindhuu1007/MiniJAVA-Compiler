package visitor;
import syntaxtree.*;
import java.util.*;

public class LiveRanges{
    Map<String, ProcInfo> procTable;
    public LiveRanges(Map<String, ProcInfo> procTable){
        this.procTable = procTable;
    }
    public void computeLiveRanges(){
        for(ProcInfo proc : procTable.values()){
            for(int i : proc.tempsUsed){
                List<Pair<Integer,Integer>> ranges = new ArrayList<>();
                boolean inRange = false;
                int start = -1;
                for(int j=0;j<proc.stmts.size();j++){
                    StmtInfo stmt = proc.stmts.get(j);
                    if(stmt.in.contains(i) || stmt.out.contains(i) || stmt.use.contains(i) || stmt.def.contains(i)){
                        if(!inRange){
                            inRange = true;
                            start = j;
                        }
                    }
                    else if(inRange){
                        inRange = false;
                        ranges.add(new Pair<>(start, j-1));
                    }
                }
                if(inRange){
                    inRange = false;
                    ranges.add(new Pair<>(start,proc.stmts.size()-1));
                }
                proc.liveRanges.put(i,ranges);
            }
        }
    }
    public void print() {
        for (String name : procTable.keySet()) {
            System.out.println("Procedure: " + name);
            ProcInfo proc = procTable.get(name);
            for (int t : proc.liveRanges.keySet()) {
                List<Pair<Integer, Integer>> ranges = proc.liveRanges.get(t);
                for (Pair<Integer, Integer> range : ranges) {
                    System.out.println("TEMP " + t + " : " + range.first() + " to " + range.second());
                }
            }
        }
    }
}