package visitor;
import syntaxtree.*;
import java.util.*;

public class Conversion extends GJNoArguDepthFirst<String> {
   private boolean debug = false;
   private boolean inMain = false;
   private int numOfParams = 0;
   private int maxArgsInCall = 0;
   private int currStackSz = 0;
   private boolean isInteger(String s) {
      boolean f = s != null && s.length() > 0;
      int start = 0;
      if (s.charAt(0) == '-') start = 1;
      for (int i = start; i < s.length(); i++) {
         if (!Character.isDigit(s.charAt(i))) {
            f = false;
            break;
         }
      }
      return f;
   }

    /**
    * f0 -> "MAIN"
    * f1 -> "["
    * f2 -> IntegerLiteral()
    * f3 -> "]"
    * f4 -> "["
    * f5 -> IntegerLiteral()
    * f6 -> "]"
    * f7 -> "["
    * f8 -> IntegerLiteral()
    * f9 -> "]"
    * f10 -> StmtList()
    * f11 -> "END"
    * f12 -> ( SpillInfo() )?
    * f13 -> ( Procedure() )*
    * f14 -> <EOF>
    */
   public String visit(Goal n) {
      String _ret=null;
      System.out.println("\t.text");
      System.out.println("\t.globl\tmain");
      System.out.println("main:");
      numOfParams = Integer.parseInt(n.f2.accept(this));
      currStackSz = Integer.parseInt(n.f5.accept(this));
      maxArgsInCall = Integer.parseInt(n.f8.accept(this));
      inMain = true;
      int outgoingArgSpace = (maxArgsInCall > 4) ? (maxArgsInCall - 4) * 4 : 0;
      System.out.println("move $fp, $sp");
      System.out.println("sw $ra, -4($fp)");
      int bytes = currStackSz * 4 + outgoingArgSpace + 4;
      System.out.println("subu $sp, $sp, " + bytes);
      n.f10.accept(this);
      System.out.println("addu $sp, $sp, " + bytes);
      System.out.println("lw $ra, -4($fp)");
      System.out.println("j $ra");
      inMain = false;
      n.f12.accept(this);
      n.f13.accept(this);
      // emit runtime helpers used by generated code
      System.out.println("\t.text");
      System.out.println("\t.globl _halloc");
      System.out.println("_halloc:");
      System.out.println("\tli $v0, 9");
      System.out.println("\tsyscall");
      System.out.println("\tj $ra");

      System.out.println("\t.text");
      System.out.println("\t.globl _print");
      System.out.println("_print:");
      System.out.println("\tli $v0, 1");
      System.out.println("\tsyscall");
      System.out.println("\tla $a0, newl");
      System.out.println("\tli $v0, 4");
      System.out.println("\tsyscall");
      System.out.println("\tj $ra");

      // data section (newline and error string)
      System.out.println("\t.data");
      System.out.println("\t.align 0");
      System.out.println("newl:\t.asciiz \"\\n\"");
      System.out.println("\t.data");
      System.out.println("\t.align 0");
      System.out.println("str_er:\t.asciiz \"ERROR: abnormal termination\\n\"");
      n.f14.accept(this);
      return _ret;
   }

   /**
    * f0 -> ( ( Label() )? Stmt() )*
    */
   public String visit(StmtList n) {
      for (Node node : n.f0.nodes) {
         NodeSequence seq = (NodeSequence)node;
         NodeOptional labelOpt = (NodeOptional)seq.elementAt(0);
         Node stmt = seq.elementAt(1);
         if(labelOpt.present()){
            String label = labelOpt.node.accept(this).toString();
            System.out.println(label + ":");
         }
         stmt.accept(this);
      }
      return null;
   }

   /**
    * f0 -> Label()
    * f1 -> "["
    * f2 -> IntegerLiteral()
    * f3 -> "]"
    * f4 -> "["
    * f5 -> IntegerLiteral()
    * f6 -> "]"
    * f7 -> "["
    * f8 -> IntegerLiteral()
    * f9 -> "]"
    * f10 -> StmtList()
    * f11 -> "END"
    * f12 -> ( SpillInfo() )?
    */
   public String visit(Procedure n) {
      String _ret = null;
      String label = n.f0.accept(this);
      System.out.println("\t.text");
      System.out.println("\t.globl\t" + label);
      System.out.println(label + ":");
      numOfParams = Integer.parseInt(n.f2.accept(this));
      currStackSz = Integer.parseInt(n.f5.accept(this));
      maxArgsInCall = Integer.parseInt(n.f8.accept(this));
      int bytes = currStackSz * 4 + 8;
      if(maxArgsInCall <= 0){
         int stackParamscnt = (numOfParams > 4) ? (numOfParams - 4) : 0;
         bytes = bytes - (stackParamscnt * 4);
      }
      //pre setup
      System.out.println("sw $fp, -8($sp)");
      System.out.println("move $fp, $sp");
      System.out.println("sw $ra, -4($fp)");
      System.out.println("subu $sp, $sp, " + bytes);
      n.f10.accept(this);
      //Aypoyaaka
      System.out.println("addu $sp, $sp, " + bytes);
      System.out.println("lw $ra, -4($fp)");
      System.out.println("lw $fp, -8($sp)");
      System.out.println("j $ra");
      n.f11.accept(this);
      n.f12.accept(this);
      return _ret;
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
    *       | ALoadStmt()
    *       | AStoreStmt()
    *       | PassArgStmt()
    *       | CallStmt()
    */
   public String visit(Stmt n) {
      String _ret=null;
      n.f0.accept(this);
      return _ret;
   }

   /**
    * f0 -> "NOOP"
    */
   public String visit(NoOpStmt n) {
      String _ret=null;
      System.out.println("nop");
      return _ret;
   }

   /**
    * f0 -> "ERROR"
    */
   public String visit(ErrorStmt n) {
      String _ret=null;
      System.out.println("# ERROR encountered");
      return _ret;
   }

   /**
    * f0 -> "CJUMP"
    * f1 -> Reg()
    * f2 -> Label()
    */
   public String visit(CJumpStmt n) {
      String _ret=null;
      String cond = n.f1.accept(this);
      String toLabel = n.f2.accept(this);
      // ensure register has $
      System.out.println("beqz " + cond + ", " + toLabel);
      return _ret;
   }

   /**
    * f0 -> "JUMP"
    * f1 -> Label()
    */
   public String visit(JumpStmt n) {
      String _ret=null;
      String toLabel = n.f1.accept(this);
      System.out.println("j " + toLabel);
      return _ret;
   }

   /**
    * f0 -> "HSTORE"
    * f1 -> Reg()
    * f2 -> IntegerLiteral()
    * f3 -> Reg()
    */
   public String visit(HStoreStmt n) {
      String _ret=null;
      String base = n.f1.accept(this);
      String offset = n.f2.accept(this);
      String src = n.f3.accept(this);
      System.out.println("sw " + src + ", " + offset + "(" + base + ")");
      return _ret;
   }

   /**
    * f0 -> "HLOAD"
    * f1 -> Reg()
    * f2 -> Reg()
    * f3 -> IntegerLiteral()
    */
   public String visit(HLoadStmt n) {
      String _ret=null;
      String dst = n.f1.accept(this);
      String base = n.f2.accept(this);
      String offset = n.f3.accept(this);
      System.out.println("lw " + dst + ", " + offset + "(" + base + ")");
   return _ret;
   }

   /**
    * f0 -> "MOVE"
    * f1 -> Reg()
    * f2 -> Exp()
    */
   public String visit(MoveStmt n) {
     String _ret=null;
     String dst = n.f1.accept(this);
     if(debug){
         System.out.println("# MOVE to " + dst);
     }
     String exp = n.f2.accept(this);
     // manaki dst -> integer avvachu, appudu li
     //lekapothe already inko register avvachu -> move
     // ledante call or label -> la
     if(exp == null){exp = "0";}
     if(exp.startsWith("BINOP:")){
        String op = "";
        String lhs = "";
        String rhs = "";
        for(int i=6;i<exp.length();i++){
            if(exp.charAt(i)==' '){
               op = exp.substring(6,i);
               int j = i+1;
               while(j<exp.length() && exp.charAt(j)!=' '){
                  j++;
               }
               lhs = exp.substring(i+1,j);
               rhs = exp.substring(j+1);
               break;
            }
        }
        switch(op) {
           case "PLUS":
              if (isInteger(rhs)) System.out.println("addi " + dst + ", " + lhs + ", " + rhs);
              else System.out.println("add " + dst + ", " + lhs + ", " + rhs);
              break;
           case "MINUS":
              if (isInteger(rhs)) System.out.println("addi " + dst + ", " + lhs + ", -" + rhs);
              else System.out.println("sub " + dst + ", " + lhs + ", " + rhs);
              break;
           case "TIMES":
              System.out.println("mul " + dst + ", " + lhs + ", " + rhs);
              break;
           case "DIV":
              System.out.println("div " + dst + ", " + lhs + ", " + rhs);
              System.out.println("mflo " + dst);
              break;
           case "LE":
              System.out.println("sle " + dst + ", " + lhs + ", " + rhs);
              break;
           case "NE":
              System.out.println("xor " + dst + ", " + lhs + ", " + rhs);
              System.out.println("sltu " + dst + ", $zero, " + dst);
              break;
           default:
              System.out.println("#");
        }
        return _ret;
     }
     else if(isInteger(exp)){
        System.out.println("li " + dst + ", " + exp);
     } 
     else if (exp.startsWith("$")){
        System.out.println("move " + dst + ", " + exp);
     } 
     else{
        System.out.println("la " + dst + ", " + exp);
     }
     return _ret;
   }

   /**
    * f0 -> "PRINT"
    * f1 -> SimpleExp()
    */
   public String visit(PrintStmt n) {
      String _ret=null;
      String arg = n.f1.accept(this);
      if(arg == null){arg = "0";}
      if(isInteger(arg)){
         System.out.println("li $a0, " + arg);
      } 
      else{
         System.out.println("move $a0, " + arg);
      }
      System.out.println("jal _print");
      return _ret;
   }

   /**
    * f0 -> "ALOAD"
    * f1 -> Reg()
    * f2 -> SpilledArg()
    */
   public String visit(ALoadStmt n) {
      String _ret=null;
      String dst = n.f1.accept(this);
      int idx = Integer.parseInt(n.f2.accept(this));
      int stackParamscnt = (numOfParams > 4) ? (numOfParams - 4) : 0;
      if (!inMain && idx < stackParamscnt) {
         int offset = idx * 4;
         System.out.println("lw " + dst + ", " + offset + "($fp)");
      } else {
         int newSpace = inMain ? (idx*4) : (idx - stackParamscnt)*4;
         int outArgSpace = (maxArgsInCall > 4) ? (maxArgsInCall - 4) * 4 : 0;
         int offset = outArgSpace + newSpace;
         System.out.println("lw " + dst + ", " + offset + "($sp)");
      }
      return _ret;
   }

   /**
    * f0 -> "ASTORE"
    * f1 -> SpilledArg()
    * f2 -> Reg()
    */
   public String visit(AStoreStmt n) {
      String _ret=null;
      int idx = Integer.parseInt(n.f1.accept(this));
      String src = n.f2.accept(this);
      int stackParamscnt = (!inMain && numOfParams > 4) ? (numOfParams - 4) : 0;
      int newSpace = inMain ? (idx*4) : (idx - stackParamscnt)*4;
      int outArgSpace = (maxArgsInCall > 4) ? (maxArgsInCall - 4) * 4 : 0;
      int offset = outArgSpace + newSpace;
      System.out.println("sw " + src + ", " + offset + "($sp)");
      return _ret;
   }

   /**
    * f0 -> "PASSARG"
    * f1 -> IntegerLiteral()
    * f2 -> Reg()
    */
   public String visit(PassArgStmt n) {
      String _ret=null;
      int idx = Integer.parseInt(n.f1.accept(this));
      String src = n.f2.accept(this);
      int offset = (idx - 1) * 4;
      System.out.println("sw " + src + ", " + offset + "($sp)");
      return _ret;
   }

   /**
    * f0 -> "CALL"
    * f1 -> SimpleExp()
    */
   public String visit(CallStmt n) {
     String _ret=null;
     String dst = n.f1.accept(this);
     if(dst.startsWith("$")){
        System.out.println("jalr " + dst);
     } else{
        System.out.println("jal " + dst);
     }
      return _ret;
   }

   /**
    * f0 -> HAllocate()
    *       | BinOp()
    *       | SimpleExp()
    */
   public String visit(Exp n) {
      return n.f0.accept(this);
   }

   /**
    * f0 -> "HALLOCATE"
    * f1 -> SimpleExp()
    */
   public String visit(HAllocate n) {
      String _ret=null;
      String size = n.f1.accept(this);
      if(size == null){size = "0";}
      if(isInteger(size)){
         System.out.println("li $a0, " + size);
      }
      else{
         System.out.println("move $a0, " + size);
      }
      System.out.println("jal _halloc");
      return "$v0";
   }

   /**
    * f0 -> Operator()
    * f1 -> Reg()
    * f2 -> SimpleExp()
    */
   public String visit(BinOp n) {
      String _ret=null;
      String op = n.f0.accept(this);
      String reg = n.f1.accept(this);
      String exp = n.f2.accept(this);
      return "BINOP:" + op + " " + reg + " " + exp;
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
      String _ret = "";
      switch(n.f0.which){
         case 0 : _ret = "LE";break;
         case 1 : _ret = "NE";break;
         case 2 : _ret = "PLUS";break;
         case 3 : _ret = "MINUS";break;
         case 4 : _ret = "TIMES";break;
         case 5 : _ret = "DIV";break;
         default : _ret = "";break;
      }
      return _ret;
   }

   /**
    * f0 -> "SPILLEDARG"
    * f1 -> IntegerLiteral()
    */
   public String visit(SpilledArg n) {
      return n.f1.accept(this);
   }

   /**
    * f0 -> Reg()
    *       | IntegerLiteral()
    *       | Label()
    */
   public String visit(SimpleExp n) {
      return n.f0.accept(this);
   }

   /**
    * f0 -> "a0"
    *       | "a1"
    *       | "a2"
    *       | "a3"
    *       | "t0"
    *       | "t1"
    *       | "t2"
    *       | "t3"
    *       | "t4"
    *       | "t5"
    *       | "t6"
    *       | "t7"
    *       | "s0"
    *       | "s1"
    *       | "s2"
    *       | "s3"
    *       | "s4"
    *       | "s5"
    *       | "s6"
    *       | "s7"
    *       | "t8"
    *       | "t9"
    *       | "v0"
    *       | "v1"
    */
   public String visit(Reg n) {
      String _ret = "";
      switch(n.f0.which){
         case 0 : _ret = "a0";break;
         case 1 : _ret = "a1";break;
         case 2 : _ret = "a2";break;
         case 3 : _ret = "a3";break;
         case 4 : _ret = "t0";break;
         case 5 : _ret = "t1";break;
         case 6 : _ret = "t2";break;
         case 7 : _ret = "t3";break;
         case 8 : _ret = "t4";break;
         case 9 : _ret = "t5";break;
         case 10 : _ret = "t6";break;
         case 11 : _ret = "t7";break;
         case 12 : _ret = "s0";break;
         case 13 : _ret = "s1";break;
         case 14 : _ret = "s2";break;
         case 15 : _ret = "s3";break;
         case 16 : _ret = "s4";break;
         case 17 : _ret = "s5";break;
         case 18 : _ret = "s6";break;
         case 19 : _ret = "s7";break;
         case 20 : _ret = "t8";break;
         case 21 : _ret = "t9";break;
         case 22 : _ret = "v0";break;
         case 23 : _ret = "v1";break;
         default : _ret = "";break;
      }
      return "$" + _ret;
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

   /**
    * f0 -> "//"
    * f1 -> SpillStatus()
    */
   public String visit(SpillInfo n) {
      String _ret=null;
      n.f1.accept(this);
      return _ret;
   }

   /**
    * f0 -> <SPILLED>
    *       | <NOTSPILLED>
    */
   public String visit(SpillStatus n) {
      return n.f0.toString();
   }
}