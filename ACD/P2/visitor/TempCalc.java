package visitor;

import syntaxtree.*;
import java.util.*;

public class TempCalc extends DepthFirstVisitor {
    
    String currentClass = "";
    String currentMethod = "";
    int tempCounter = 0;
    private final Map<String, Integer> tempCount = new HashMap<>();

    private String methodName() {
        return currentClass + "." + currentMethod;
    }

    public Map<String, Integer> getTempCount() {
        return tempCount;
    }

    public void visit(ClassDeclaration n) {
        currentClass = n.f1.f0.toString();
        n.f3.accept(this);
        n.f4.accept(this);
    }

    public void visit(ClassExtendsDeclaration n) {
        currentClass = n.f1.f0.toString();
        n.f5.accept(this);
        n.f6.accept(this);
    }

    public void visit(MethodDeclaration n) {
        currentMethod = n.f2.f0.toString();
        tempCounter = 0;
        n.f4.accept(this);
        n.f7.accept(this);
        n.f8.accept(this);
        n.f10.accept(this);
        tempCount.put(methodName(), tempCounter);
    }

    public void visit(AndExpression n) {
        tempCounter++;
        n.f0.accept(this);
        n.f2.accept(this);
   }

   public void visit(ORExpression n) {
      tempCounter++;
      n.f0.accept(this);
      n.f2.accept(this);
   }

   public void visit(CompareExpression n) {
      tempCounter++;
      n.f0.accept(this);
      n.f2.accept(this);
   }

   public void visit(PlusExpression n) {
      tempCounter++;
      n.f0.accept(this);
      n.f2.accept(this);
   }

   public void visit(MinusExpression n) {
      tempCounter++;
      n.f0.accept(this);
      n.f2.accept(this);
   }

   public void visit(TimesExpression n) {
      tempCounter++;
      n.f0.accept(this);
      n.f2.accept(this);
   }

}