package visitor;
import java.util.*;

public class ProcInfo{
      public String procName;
      public int argCount = 0;
      public Set<Integer> tempsUsed;
      public Map<String,Integer> labelLine;
      public List<StmtInfo> stmts;
      public Map<Integer,List<Pair<Integer,Integer>>> liveRanges;
      public ProcInfo(String name){
         this.procName = name;
         this.tempsUsed = new HashSet<>();
         this.labelLine = new HashMap<>();
         this.stmts = new ArrayList<>();
         this.liveRanges = new HashMap<>();
      }
   }