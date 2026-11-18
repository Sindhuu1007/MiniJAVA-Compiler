package visitor;
import syntaxtree.*;
import java.util.*;

public class Printing extends GJNoArguDepthFirst<String> {

   private enum Mode { USE, DEF }

   private final Map<String, ProcInfo> procTable;
   private final Map<String, LSA.Allocation> allocations;

   private LSA.Allocation currentAlloc;
   private String currentProc;
   private Map<Integer,String> regAssign = Collections.emptyMap();
   private Map<Integer,Integer> spillAssign = Collections.emptyMap();
   private Mode tempType = Mode.USE;
   private Map<Integer,String> loadedSpills = new HashMap<>();
   private List<String> specificRegs = new ArrayList<>();
   private boolean usedSpill;
   private int incomingArgSlots;
   private int stackSlots;

   private int currentProcMaxCallArgs;
   private List<String> calleeSaveOps = Collections.emptyList();
   private List<String> calleeRestoreOps = Collections.emptyList();
   private boolean hasCall;

   private static class CallInfo {
      boolean hasCall;
      int maxArgs;
   }

   private int calleeSaveBase;
   private int callerSaveBase;
   private int callerSaveSlots;

   public Printing(Map<String, ProcInfo> procTable, Map<String, LSA.Allocation> allocations){
      this.procTable = procTable;
      this.allocations = allocations != null ? allocations : Collections.emptyMap();
   }

   private void beginStatement(){
      preAction = new ArrayList<>();
      statement = new ArrayList<>();
      postAction = new ArrayList<>();
      loadedSpills = new HashMap<>();
      specificRegs = new ArrayList<>(Arrays.asList("v1", "v0"));
      tempType = Mode.USE;
   }

   private List<String> preAction = new ArrayList<>();
   private List<String> statement = new ArrayList<>();
   private List<String> postAction = new ArrayList<>();

   private void reset(){
      currentProc = null;
      currentAlloc = null;
      regAssign = Collections.emptyMap();
      spillAssign = Collections.emptyMap();
      calleeSaveOps = Collections.emptyList();
      calleeRestoreOps = Collections.emptyList();
      tempType = Mode.USE;
      hasCall = false;
      callerSaveSlots = 0;
      callerSaveBase = 0;
      calleeSaveBase = 0;
   }

   private void emit(String s){
      statement.add(s);
   }

   /**
    * f0 -> "MAIN"
    * f1 -> StmtList()
    * f2 -> "END"
    * f3 -> ( Procedure() )*
    * f4 -> <EOF>
    */
   public String visit(Goal n) {
      startProc("MAIN", true);
      int argCount = procTable.get("MAIN") != null ? procTable.get("MAIN").argCount : 0;
      System.out.println("MAIN [" + argCount + "] [" + stackSlots + "] [" + currentProcMaxCallArgs + "]");
      n.f1.accept(this);
      System.out.println("END");
      System.out.println(usedSpill ? "//SPILLED" : "//NOTSPILLED");
      reset();
      n.f3.accept(this);
      return null;
   }

   /**
    * f0 -> ( ( Label() )? Stmt() )*
    */
   public String visit(StmtList n) {
      if(!n.f0.present()) return null;
      Enumeration<Node> elements = n.f0.elements();
      while(elements.hasMoreElements()){
         NodeSequence seq = (NodeSequence)elements.nextElement();
         NodeOptional opt = (NodeOptional)seq.elementAt(0);
         if(opt.present()){
            Label lbl = (Label)opt.node;
            System.out.println(lbl.f0.toString());
         }
         Stmt stmt = (Stmt)seq.elementAt(seq.size() - 1);
         beginStatement();
         stmt.accept(this);
         for(String s : preAction){
            System.out.println(s);
         }
         for(String s : statement){
            System.out.println(s);
         }
         for(String s : postAction){
            System.out.println(s);
         }
      }
      return null;
   }

   /**
    * f0 -> Label()
    * f1 -> "["
    * f2 -> IntegerLiteral()
    * f3 -> "]"
    * f4 -> StmtExp()
    */
   public String visit(Procedure n) {
      String name = n.f0.f0.toString();
      startProc(name, false);
      int argCount = procTable.get(name) != null ? procTable.get(name).argCount : 0;
      System.out.println(name + " [" + argCount + "] [" + stackSlots + "] [" + currentProcMaxCallArgs + "]");
      n.f4.accept(this);
      System.out.println("END");
      System.out.println(usedSpill ? "//SPILLED" : "//NOTSPILLED");
      reset();
      return null;
   }

   /**
    * f0 -> NoOpStmt()
    *       | ErrorStmt()
    *       | CJumpStmt()
    *       | JumpStmt()
    *       | HStoreStmt()
    *       | HLoadStmt()
    *       | MoveStmt()
    *       | PrintStmt()
    */
   public String visit(Stmt n) {
      return n.f0.accept(this);
   }

   /**
    * f0 -> "NOOP"
    */
   public String visit(NoOpStmt n) {
      emit("NOOP");
      return null;
   }

   /**
    * f0 -> "ERROR"
    */
   public String visit(ErrorStmt n) {
      emit("ERROR");
      return null;
   }

   /**
    * f0 -> "CJUMP"
    * f1 -> Temp()
    * f2 -> Label()
    */
   public String visit(CJumpStmt n) {
      tempType = Mode.USE;
      String cond = n.f1.accept(this);
      String label = n.f2.accept(this);
      emit("CJUMP " + cond + " " + label);
      return null;
   }

   /**
    * f0 -> "JUMP"
    * f1 -> Label()
    */
   public String visit(JumpStmt n) {
      String label = n.f1.accept(this);
      emit("JUMP " + label);
      return null;
   }

   /**
    * f0 -> "HSTORE"
    * f1 -> Temp()
    * f2 -> IntegerLiteral()
    * f3 -> Temp()
    */
   public String visit(HStoreStmt n) {
      tempType = Mode.USE;
      String base = n.f1.accept(this);
      String offset = n.f2.accept(this);
      tempType = Mode.USE;
      String src = n.f3.accept(this);
      emit("HSTORE " + base + " " + offset + " " + src);
      return null;
   }

   /**
    * f0 -> "HLOAD"
    * f1 -> Temp()
    * f2 -> Temp()
    * f3 -> IntegerLiteral()
    */
   public String visit(HLoadStmt n) {
      tempType = Mode.DEF;
      String dest = n.f1.accept(this);
      tempType = Mode.USE;
      String base = n.f2.accept(this);
      String offset = n.f3.accept(this);
      emit("HLOAD " + dest + " " + base + " " + offset);
      return null;
   }

   /**
    * f0 -> "MOVE"
    * f1 -> Temp()
    * f2 -> Exp()
    */
   public String visit(MoveStmt n) {
      tempType = Mode.DEF;
      String dest = n.f1.accept(this);
      tempType = Mode.USE;
      String rhs = n.f2.accept(this);
      emit("MOVE " + dest + " " + rhs);
      return null;
   }

   /**
    * f0 -> "PRINT"
    * f1 -> SimpleExp()
    */
   public String visit(PrintStmt n) {
      tempType = Mode.USE;
      String arg = n.f1.accept(this);
      emit("PRINT " + arg);
      return null;
   }

   /**
    * f0 -> Call()
    *       | HAllocate()
    *       | BinOp()
    *       | SimpleExp()
    */
   public String visit(Exp n) {
      return n.f0.accept(this);
   }

   /**
    * f0 -> "BEGIN"
    * f1 -> StmtList()
    * f2 -> "RETURN"
    * f3 -> SimpleExp()
    * f4 -> "END"
    */
   public String visit(StmtExp n) {
      for(String save : calleeSaveOps){
         System.out.println(save);
      }
      emitArgumentMoves();
      n.f1.accept(this);

      beginStatement();
      tempType = Mode.USE;
      String ret = n.f3.accept(this);
      if(!"v0".equals(ret)){
         emit("MOVE v0 " + ret);
      }
      for(String restore : calleeRestoreOps){
         emit(restore);
      }
      for(String s : preAction){
         System.out.println(s);
      }
      for(String s : statement){
         System.out.println(s);
      }
      for(String s : postAction){
         System.out.println(s);
      }
      return null;
   }

   /**
    * f0 -> "CALL"
    * f1 -> SimpleExp()
    * f2 -> "("
    * f3 -> ( Temp() )*
    * f4 -> ")"
    */
   public String visit(Call n) {
      String target = n.f1.accept(this);
      List<String> args = new ArrayList<>();
      if(n.f3.present()){
         for(Enumeration<Node> e = n.f3.elements(); e.hasMoreElements();){
            Temp t = (Temp)e.nextElement();
            tempType = Mode.USE;
            args.add(t.accept(this));
         }
      }
      int count = args.size();
      hasCall = true;
      if(count > currentProcMaxCallArgs){
         currentProcMaxCallArgs = count;
      }
      if(callerSaveSlots > 0){
         for(int i = 0; i < 10; i++){
            emit("ASTORE SPILLEDARG " + (callerSaveBase + i) + " t" + i);
         }
      }
      for(int i = 0; i < count; i++){
         String arg = args.get(i);
         if(i < 4){
            emit("MOVE a" + i + " " + arg);
         } else {
            emit("PASSARG " + (i - 3) + " " + arg);
         }
      }
      emit("CALL " + target);
      if(callerSaveSlots > 0){
         for(int i = 0; i < 10; i++){
            emit("ALOAD t" + i + " SPILLEDARG " + (callerSaveBase + i));
         }
      }
      return "v0";
   }

   /**
    * f0 -> "HALLOCATE"
    * f1 -> SimpleExp()
    */
   public String visit(HAllocate n) {
      String arg = n.f1.accept(this);
      String sizeReg = "";
      if(specificRegs.isEmpty()){
         sizeReg = "v1";
      } else {
         sizeReg = specificRegs.remove(0);
      }
      preAction.add("MOVE " + sizeReg + " " + arg);
      preAction.add("MOVE " + sizeReg + " PLUS " + sizeReg + " 4");
      return "HALLOCATE " + sizeReg;
   }

   /**
    * f0 -> Operator()
    * f1 -> Temp()
    * f2 -> SimpleExp()
    */
   public String visit(BinOp n) {
      String op = n.f0.accept(this);
      tempType = Mode.USE;
      String lhs = n.f1.accept(this);
      String rhs = n.f2.accept(this);
      return op + " " + lhs + " " + rhs;
   }

   /**
    * f0 -> "LE"
    *       | "NE"
    *       | "PLUS"
    *       | "MINUS"
    *       | "TIMES"
    *       | "DIV"
    */
   public String visit(Operator n) {
      return n.f0.choice.toString();
   }

   /**
    * f0 -> Temp()
    *       | IntegerLiteral()
    *       | Label()
    */
   public String visit(SimpleExp n) {
      return n.f0.accept(this);
   }

   /**
    * f0 -> "TEMP"
    * f1 -> IntegerLiteral()
    */
   public String visit(Temp n) {
      int temp = Integer.parseInt(n.f1.f0.toString());
       if(regAssign.containsKey(temp)){
         String reg = regAssign.get(temp);
         specificRegs.remove(reg);
         return reg;
      }
      if(!spillAssign.containsKey(temp)){
         return "TEMP " + temp;
      }
      usedSpill = true;
      int slot = spillAssign.get(temp);
      if(tempType == Mode.DEF){
         String reg = "v0";
         postAction.add("ASTORE SPILLEDARG " + slot + " " + reg);
         return reg;
      }
      if(loadedSpills.containsKey(temp)){
         return loadedSpills.get(temp);
      }
      String speReg = "";
      if(specificRegs.isEmpty()){
         speReg = "v1";
      } else {
         speReg = specificRegs.remove(0);
      }
      preAction.add("ALOAD " + speReg + " SPILLEDARG " + slot);
      loadedSpills.put(temp, speReg);
      return speReg;
   }

   /**
    * f0 -> <INTEGER_LITERAL>
    */
   public String visit(IntegerLiteral n) {
      return n.f0.toString();
   }

   /**
    * f0 -> <IDENTIFIER>
    */
   public String visit(Label n) {
      return n.f0.toString();
   }

   private void emitArgumentMoves(){
      if(procTable.get(currentProc) == null) return;
      int args = procTable.get(currentProc).argCount;
      for(int i = 0; i < args; i++){
         int temp = i;
         String register = regAssign.get(temp);
         Integer spillSlot = spillAssign.get(temp);
         if(register != null){
            if(i < 4){
               System.out.println("MOVE " + register + " a" + i);
            } else {
                  System.out.println("ALOAD " + register + " SPILLEDARG " + (i - 4));
            }
         } else if(spillSlot != null){
            usedSpill = true;
            if(i < 4){
               System.out.println("ASTORE SPILLEDARG " + spillSlot + " a" + i);
            } else {
                  System.out.println("ALOAD v1 SPILLEDARG " + (i - 4));
               System.out.println("ASTORE SPILLEDARG " + spillSlot + " v1");
            }
         }
      }
   }

   private int startProc(String name, boolean mainProc){
      currentProc = name;
      currentAlloc = allocations.get(name);
      regAssign = (currentAlloc != null && currentAlloc.allocMap != null) ? currentAlloc.allocMap : Collections.emptyMap();
      Map<Integer,Integer> rawSpills = (currentAlloc != null && currentAlloc.spillMap != null) ? currentAlloc.spillMap : Collections.emptyMap();
      incomingArgSlots = Math.max(0, (procTable.get(name) != null ? procTable.get(name).argCount : 0) - 4);
      int calleeSlots = mainProc ? 0 : 8;
      ProcInfo info = procTable.get(name);
      CallInfo callInfo = new CallInfo();
      for(StmtInfo stmtInfo : info.stmts){
         if(!(stmtInfo.stmt instanceof MoveStmt)) continue;
         MoveStmt move = (MoveStmt)stmtInfo.stmt;
         Exp exp = move.f2;
         if(!(exp.f0.choice instanceof Call)) continue;
         Call call = (Call)exp.f0.choice;
         callInfo.hasCall = true;
         int count = 0;
         if(call.f3.present()){
            for(Enumeration<Node> e = call.f3.elements(); e.hasMoreElements();){
               e.nextElement();
               count++;
            }
         }
         if(count > callInfo.maxArgs) callInfo.maxArgs = count;
      }
      currentProcMaxCallArgs = callInfo.maxArgs;
      hasCall = callInfo.hasCall;
      callerSaveSlots = hasCall ? 10 : 0;
      calleeSaveBase = incomingArgSlots;
      callerSaveBase = incomingArgSlots + calleeSlots;
      int spillBase = callerSaveBase + callerSaveSlots;
      Map<Integer,Integer> tempSpills = new HashMap<>();
      for(Map.Entry<Integer,Integer> entry : rawSpills.entrySet()){
         tempSpills.put(entry.getKey(), spillBase + entry.getValue());
      }
      spillAssign = tempSpills;
      usedSpill = currentAlloc != null && currentAlloc.stackSlotsNeeded > 0;
      calleeSaveOps = new ArrayList<>();
      calleeRestoreOps = new ArrayList<>();
      if(!mainProc){
         for(int i = 0; i < 8; i++){
            int slot = calleeSaveBase + i;
            calleeSaveOps.add("ASTORE SPILLEDARG " + slot + " s" + i);
            calleeRestoreOps.add("ALOAD s" + i + " SPILLEDARG " + slot);
         }
      }
      int spillCount = currentAlloc != null ? currentAlloc.stackSlotsNeeded : 0;
      stackSlots = incomingArgSlots + calleeSlots + callerSaveSlots + spillCount;
      return stackSlots;
   }
}