package visitor;
import syntaxtree.*;
import java.util.*;

public class InOut{
    public Map<String, ProcInfo> procTable;
    public InOut(Map<String, ProcInfo> procTable){
        this.procTable = procTable;
    }
    public void computeInOut(){
        for(ProcInfo proc : procTable.values()){
            boolean changed = true;
            while(changed){
                changed = false;
                for(int i = proc.stmts.size() - 1; i >= 0; i--){
                    StmtInfo stmtInfo = proc.stmts.get(i);
                    Set<Integer> oldIn = new HashSet<>(stmtInfo.in);
                    Set<Integer> oldOut = new HashSet<>(stmtInfo.out);
                    //mundhu out calculate chesaaka in easy kaabatti
                    //in emo use[n] U (out[n] - def[n])
                    //out[n] emo U in[s] s->succ[n];
                    //out[n] calculation
                    Set<Integer> newOut = new HashSet<>();
                    for(int succIndex : stmtInfo.succ){
                        if(succIndex >= proc.stmts.size()){
                            continue;
                        }
                        newOut.addAll(proc.stmts.get(succIndex).in);
                    }
                    stmtInfo.out = newOut;
                    //in[n] calculation
                    Set<Integer> newIn = new HashSet<>();
                    newIn.addAll(stmtInfo.use);
                    for(int o : stmtInfo.out){
                        if(!stmtInfo.def.contains(o)){
                            newIn.add(o);
                        }
                    }
                    stmtInfo.in = newIn;
                    if(!oldIn.equals(stmtInfo.in) || !oldOut.equals(stmtInfo.out)){
                        changed = true;
                    }
                }
            }
        }
    }
    public void print() {
        for (String name : procTable.keySet()) {
            System.out.println("Procedure: " + name);
            ProcInfo p = procTable.get(name);
            for (StmtInfo s : p.stmts) {
                System.out.println("Stmt " + s.index + " IN=" + s.in + " OUT=" + s.out);
            }
        }
    }
}