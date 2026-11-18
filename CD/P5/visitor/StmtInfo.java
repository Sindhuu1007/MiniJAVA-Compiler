package visitor;
import java.util.*;
import syntaxtree.*;

class StmtInfo{
    public Node stmt;
    public int index;
    public Set<Integer> use;
    public Set<Integer> def;
    public Set<Integer> in;
    public Set<Integer> out;
    public Set<Integer> succ;
    public StmtInfo(Node stmt, int index){
        this.stmt = stmt;
        this.index = index;
        this.use = new HashSet<>();
        this.def = new HashSet<>();
        this.in = new HashSet<>();
        this.out = new HashSet<>();
        this.succ = new HashSet<>();
    }
}