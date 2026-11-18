import visitor.GJDepthFirst;
import syntaxtree.*;
import java.util.*;

public class MiniIRGen extends GJDepthFirst<MiniIRGen.IR, MiniIRGen.MethodContext> {
    private final SymbolTable st;
    private final StringBuilder out = new StringBuilder();
    private int labelCounter = 0;
    private String currentClass = null;
    private String currentMethod = null;
    private MethodContext currentContext = null;
    private int indentLevel = 0;

    public MiniIRGen(SymbolTable st) {
        this.st = st;
    }

    static class IR {
        final String expr;
        final String type;
        IR(String expr, String type) {
            this.expr = expr;
            this.type = type;
        }
    }

    static class MethodContext {
        int tempCounter;
    }
    
    public IR visit(Goal n, MethodContext argu) {
        n.f0.accept(this, null);
        n.f1.accept(this, null);
        n.f2.accept(this, null);
        n.f3.accept(this, null);
        System.out.print(out.toString());
        return null;
    }

    private String newLabel() {
        return "L" + (labelCounter++);
    }

    private String newTemp() {
        String tempName = "TEMP " + currentContext.tempCounter;
        currentContext.tempCounter++;
        return tempName;
    }
    
    public IR visit(MainClass n, MethodContext argu) {
        String mainClass = n.f1.f0.toString();
        currentClass = mainClass;
        currentMethod = "main";
        MethodContext previousContext = currentContext;
        MethodContext ctx = createContext(currentClass, currentMethod);
        currentContext = ctx;
        tempTypes(currentClass, currentMethod);
        out.append("MAIN\n");
        int previousIndent = indentLevel;
        indentLevel = 1;
        n.f14.accept(this, ctx);
        indentLevel = previousIndent;
        out.append("END\n\n");
        currentContext = previousContext;
        currentMethod = null;
        currentClass = null;
        return null;
    }

    public IR visit(ClassDeclaration n, MethodContext argu) {
        currentClass = n.f1.f0.toString();
        n.f4.accept(this, null);
        currentClass = null;
        return null;
    }

    public IR visit(ClassExtendsDeclaration n, MethodContext argu) {
        currentClass = n.f1.f0.toString();
        n.f6.accept(this, null);
        currentClass = null;
        return null;
    }

    public IR visit(MethodDeclaration n, MethodContext argu) {
        String methodName = n.f2.f0.toString();
        String previousMethod = currentMethod;
        MethodContext previousContext = currentContext;
        int previousIndent = indentLevel;
        currentMethod = methodName;
        MethodContext ctx = createContext(currentClass, currentMethod);
        currentContext = ctx;
        tempTypes(currentClass, currentMethod);
        ClassInfo cinfo = st.classes.get(currentClass);
        MethodInfo minfo = cinfo.methods.get(methodName);
        int argCount = minfo.paramOrder.size() + 1;
        out.append(currentClass).append("_").append(methodName).append(" [").append(argCount).append("]\n");
        indentLevel = 1;
        emit("BEGIN");
        indentLevel = 2;
        n.f8.accept(this, ctx);
        IR ret = n.f10.accept(this, ctx);
        emit("RETURN " + ret.expr);
        indentLevel = 1;
        emit("END");
        out.append("\n");
        currentContext = previousContext;
        currentMethod = previousMethod;
        indentLevel = previousIndent;
        return null;
    }
    
    public IR visit(Statement n, MethodContext argu) {
        return n.f0.accept(this, argu);
    }
    
    public IR visit(Block n, MethodContext argu) {
        n.f1.accept(this, argu);
        return null;
    }
    
    public IR visit(AssignmentStatement n, MethodContext argu) {
        String varName = n.f0.f0.toString();
        IR rhs = n.f2.accept(this, argu);
        String rhsExpr = rhs.expr;
        String lhsTemp = null;
        if (currentClass != null && currentMethod != null) {
            lhsTemp = st.getTempForVariable(currentClass, currentMethod, varName);
        }
        if (lhsTemp != null) {
            emit("MOVE " + lhsTemp + " " + rhsExpr);
            if (isClassType(rhs.type)) {
                st.recordTempClass(lhsTemp, rhs.type);
            }
        } else {
            int offset = st.getFieldOffset(currentClass, varName);
            if (offset < 0) {
                emit("MOVE " + varName + " " + rhsExpr);
            } else {
                emit("HSTORE TEMP 0 " + offset + " " + rhsExpr);
            }
        }
        return null;
    }
    
    public IR visit(ArrayAssignmentStatement n, MethodContext argu) {
        IR array = valueFromIdentifier(n.f0.f0.toString());
        IR index = n.f2.accept(this, argu);
        IR value = n.f5.accept(this, argu);

        String arrTemp = ensureTemp(array);
        String idxTemp = ensureTemp(index);
        String valTemp = ensureTemp(value);

        String offset = newTemp();
        emit("MOVE " + offset + " TIMES " + idxTemp + " 4");
        String addr = newTemp();
        emit("MOVE " + addr + " PLUS " + arrTemp + " 4");
        emit("MOVE " + addr + " PLUS " + addr + " " + offset);
        emit("HSTORE " + addr + " 0 " + valTemp);
        return null;
    }
    
    public IR visit(IfthenStatement n, MethodContext argu) {
        IR cond = n.f2.accept(this, argu);
        String condTemp = ensureTemp(cond);
        String endLabel = newLabel();
        emit("CJUMP " + condTemp + " " + endLabel);
        n.f4.accept(this, argu);
        emitLabel(endLabel);
        return null;
    }
    
    public IR visit(IfthenElseStatement n, MethodContext argu) {
        IR cond = n.f2.accept(this, argu);
        String condTemp = ensureTemp(cond);
        String elseLabel = newLabel();
        String endLabel = newLabel();
        emit("CJUMP " + condTemp + " " + elseLabel);
        n.f4.accept(this, argu);
        emit("JUMP " + endLabel);
        emitLabel(elseLabel);
        n.f6.accept(this, argu);
        emitLabel(endLabel);
        return null;
    }
    
    public IR visit(WhileStatement n, MethodContext argu) {
        String startLabel = newLabel();
        String endLabel = newLabel();
        emitLabel(startLabel);
        IR cond = n.f2.accept(this, argu);
        String condTemp = ensureTemp(cond);
        emit("CJUMP " + condTemp + " " + endLabel);
        n.f4.accept(this, argu);
        emit("JUMP " + startLabel);
        emitLabel(endLabel);
        return null;
    }
    
    public IR visit(PrintStatement n, MethodContext argu) {
        IR expr = n.f2.accept(this, argu);
        emit("PRINT " + expr.expr);
        return null;
    }
    
    public IR visit(Expression n, MethodContext argu) {
        return n.f0.accept(this, argu);
    }
    
    public IR visit(AndExpression n, MethodContext argu) {
        IR lhs = n.f0.accept(this, argu);
        String lhsTemp = ensureTemp(lhs);
        String falseLabel = newLabel();
        String endLabel = newLabel();
        String result = newTemp();
        emit("MOVE " + result + " 0");
        emit("CJUMP " + lhsTemp + " " + falseLabel);
        IR rhs = n.f2.accept(this, argu);
        String rhsTemp = ensureTemp(rhs);
        emit("CJUMP " + rhsTemp + " " + falseLabel);
        emit("MOVE " + result + " 1");
        emit("JUMP " + endLabel);
        emitLabel(falseLabel);
        emitLabel(endLabel);
        return new IR(result, "boolean");
    }

    public IR visit(OrExpression n, MethodContext argu) {
        IR lhs = n.f0.accept(this, argu);
        String lhsTemp = ensureTemp(lhs);
        String checkRhs = newLabel();
        String endLabel = newLabel();
        String result = newTemp();
        emit("MOVE " + result + " 0");
        emit("CJUMP " + lhsTemp + " " + checkRhs);
        emit("MOVE " + result + " 1");
        emit("JUMP " + endLabel);
        emitLabel(checkRhs);
        IR rhs = n.f2.accept(this, argu);
        String rhsTemp = ensureTemp(rhs);
        emit("CJUMP " + rhsTemp + " " + endLabel);
        emit("MOVE " + result + " 1");
        emitLabel(endLabel);
        return new IR(result, "boolean");
    }
    
    public IR visit(CompareExpression n, MethodContext argu) {
        IR lhs = n.f0.accept(this, argu);
        IR rhs = n.f2.accept(this, argu);
        String result = newTemp();
        emit("MOVE " + result + " LE " + lhs.expr + " " + rhs.expr);
        return new IR(result, "boolean");
    }
    
    public IR visit(neqExpression n, MethodContext argu) {
        IR lhs = n.f0.accept(this, argu);
        IR rhs = n.f2.accept(this, argu);
        String result = newTemp();
        emit("MOVE " + result + " NE " + lhs.expr + " " + rhs.expr);
        return new IR(result, "boolean");
    }
    
    public IR visit(AddExpression n, MethodContext argu) {
        return binaryOp("PLUS", n.f0.accept(this, argu), n.f2.accept(this, argu), "int");
    }
    
    public IR visit(MinusExpression n, MethodContext argu) {
        return binaryOp("MINUS", n.f0.accept(this, argu), n.f2.accept(this, argu), "int");
    }
    
    public IR visit(TimesExpression n, MethodContext argu) {
        return binaryOp("TIMES", n.f0.accept(this, argu), n.f2.accept(this, argu), "int");
    }
    
    public IR visit(DivExpression n, MethodContext argu) {
        return binaryOp("DIV", n.f0.accept(this, argu), n.f2.accept(this, argu), "int");
    }
    
    public IR visit(ArrayLookup n, MethodContext argu) {
        IR array = n.f0.accept(this, argu);
        IR index = n.f2.accept(this, argu);
        String arrTemp = ensureTemp(array);
        String idxTemp = ensureTemp(index);
        String offset = newTemp();
        emit("MOVE " + offset + " TIMES " + idxTemp + " 4");
        String addr = newTemp();
        emit("MOVE " + addr + " PLUS " + arrTemp + " 4");
        emit("MOVE " + addr + " PLUS " + addr + " " + offset);
        String result = newTemp();
        emit("HLOAD " + result + " " + addr + " 0");
        return new IR(result, "int");
    }
    
    public IR visit(ArrayLength n, MethodContext argu) {
        IR array = n.f0.accept(this, argu);
        String arrTemp = ensureTemp(array);
        String len = newTemp();
        emit("HLOAD " + len + " " + arrTemp + " 0");
        return new IR(len, "int");
    }
    
    public IR visit(MessageSend n, MethodContext argu) {
        IR objVal = n.f0.accept(this, argu);
        String objTemp = ensureTemp(objVal);
        String objClass = objVal.type;
        if (objClass == null) {
            objClass = st.getClassForTemp(objTemp);
        }
        String methodName = n.f2.f0.toString();
        int offset = st.getMethodOffset(objClass, methodName);
        String vtTemp = newTemp();
        emit("HLOAD " + vtTemp + " " + objTemp + " 0");
        String fnTemp = newTemp();
        emit("HLOAD " + fnTemp + " " + vtTemp + " " + offset);
        List<IR> args = collectArgs(n.f4, argu);
        StringBuilder call = new StringBuilder();
        call.append("CALL ").append(fnTemp).append(" (").append(objTemp);
        for (IR arg : args) {
            String argTemp = ensureTemp(arg);
            call.append(" ").append(argTemp);
        }
        call.append(")");
        String returnType = st.getMethodReturnType(objClass, methodName);
        return new IR(call.toString(), returnType);
    }
    
    public IR visit(ExpressionList n, MethodContext argu) {
        return n.f0.accept(this, argu);
    }

    public IR visit(ExpressionRest n, MethodContext argu) {
        return n.f1.accept(this, argu);
    }
    
    public IR visit(IntegerLiteral n, MethodContext argu) {
        return new IR(n.f0.toString(), "int");
    }

    
    public IR visit(TrueLiteral n, MethodContext argu) {
        return new IR("1", "boolean");
    }

    public IR visit(FalseLiteral n, MethodContext argu) {
        return new IR("0", "boolean");
    }

    public IR visit(Identifier n, MethodContext argu) {
        return valueFromIdentifier(n.f0.toString());
    }

    public IR visit(ThisExpression n, MethodContext argu) {
        return new IR("TEMP 0", currentClass);
    }

    public IR visit(ArrayAllocationExpression n, MethodContext argu) {
        IR sizeVal = n.f3.accept(this, argu);
        String sizeTemp = ensureTemp(sizeVal);
        String fullSize = newTemp();
        emit("MOVE " + fullSize + " PLUS " + sizeTemp + " 1");
        String bytes = newTemp();
        emit("MOVE " + bytes + " TIMES " + fullSize + " 4");
        String arr = newTemp();
        emit("MOVE " + arr + " HALLOCATE " + bytes);
        emit("HSTORE " + arr + " 0 " + sizeTemp);
        String limit = newTemp();
        emit("MOVE " + limit + " MINUS " + sizeTemp + " 1");
        String index = newTemp();
        emit("MOVE " + index + " 0");
        String loopLabel = newLabel();
        String endLabel = newLabel();
        emitLabel(loopLabel);
        String cond = newTemp();
        emit("MOVE " + cond + " LE " + index + " " + limit);
        emit("CJUMP " + cond + " " + endLabel);
        String offset = newTemp();
        emit("MOVE " + offset + " TIMES " + index + " 4");
        String addr = newTemp();
        emit("MOVE " + addr + " PLUS " + arr + " 4");
        emit("MOVE " + addr + " PLUS " + addr + " " + offset);
        emit("HSTORE " + addr + " 0 0");
        emit("MOVE " + index + " PLUS " + index + " 1");
        emit("JUMP " + loopLabel);
        emitLabel(endLabel);
        return new IR(arr, "int[]");
    }

    public IR visit(AllocationExpression n, MethodContext argu) {
        String className = n.f1.f0.toString();
        ClassInfo ci = st.classes.get(className);
        int vtableBytes = Math.max(1, ci.vTable.size()) * 4;
        String vt = newTemp();
        emit("MOVE " + vt + " HALLOCATE " + vtableBytes);
        int offset = 0;
        for (MethodInfo mi : ci.vTable) {
            emit("HSTORE " + vt + " " + offset + " " + mi.methodLabel);
            offset += 4;
        }
        int objBytes = 4 + ci.fieldOffsets.size() * 4;
        String obj = newTemp();
        emit("MOVE " + obj + " HALLOCATE " + objBytes);
        emit("HSTORE " + obj + " 0 " + vt);
        for (Map.Entry<String, Integer> entry : ci.fieldOffsets.entrySet()) {
            emit("HSTORE " + obj + " " + entry.getValue() + " 0");
        }
        st.recordTempClass(obj, className);
        return new IR(obj, className);
    }

    public IR visit(NotExpression n, MethodContext argu) {
        IR val = n.f1.accept(this, argu);
        String temp = ensureTemp(val);
        String trueLabel = newLabel();
        String endLabel = newLabel();
        String result = newTemp();
        emit("MOVE " + result + " 1");
        emit("CJUMP " + temp + " " + trueLabel);
        emit("MOVE " + result + " 0");
        emit("JUMP " + endLabel);
        emitLabel(trueLabel);
        emitLabel(endLabel);
        return new IR(result, "boolean");
    }

    
    public IR visit(BracketExpression n, MethodContext argu) {
        return n.f1.accept(this, argu);
    }

    private MethodContext createContext(String className, String methodName) {
        MethodContext ctx = new MethodContext();
        ctx.tempCounter = Math.max(1, nextTempIndex(className, methodName));
        return ctx;
    }

    private int nextTempIndex(String className, String methodName) {
        if (className == null || methodName == null) {
            return 1;
        }
        ClassInfo cinfo = st.classes.get(className);
        if (cinfo == null) {
            return 1;
        }
        MethodInfo minfo = cinfo.methods.get(methodName);
        if (minfo == null) {
            return 1;
        }
        int max = 0;
        for (int v : minfo.paramTemp.values()) {
            max = Math.max(max, v);
        }
        for (int v : minfo.localTemp.values()) {
            max = Math.max(max, v);
        }
        return max + 1;
    }

    private void tempTypes(String className, String methodName) {
        if (className == null || methodName == null) {
            return;
        }
        if (!"main".equals(methodName)) {
            st.recordTempClass("TEMP 0", className);
        }
        ClassInfo cinfo = st.classes.get(className);
        if (cinfo == null) {
            return;
        }
        MethodInfo minfo = cinfo.methods.get(methodName);
        if (minfo == null) {
            return;
        }
        for (Map.Entry<String, Integer> entry : minfo.paramTemp.entrySet()) {
            String type = minfo.parameters.get(entry.getKey());
            if (isClassType(type)) {
                st.recordTempClass("TEMP " + entry.getValue(), type);
            }
        }
    }

    private boolean isClassType(String type) {
        return type != null && st.classes.containsKey(type);
    }

    private void emit(String line) {
        for (int i = 0; i < indentLevel; i++) {
            out.append("  ");
        }
        out.append(line).append("\n");
    }

    private void emitLabel(String label) {
        out.append(label).append(" NOOP\n");
    }

    private IR binaryOp(String op, IR left, IR right, String type) {
        String result = newTemp();
        emit("MOVE " + result + " " + op + " " + left.expr + " " + right.expr);
        return new IR(result, type);
    }

    private String ensureTemp(IR val) {
        if (val.expr.startsWith("TEMP ")) {
            return val.expr;
        }
        String tmp = newTemp();
        emit("MOVE " + tmp + " " + val.expr);
        if (isClassType(val.type)) {
            st.recordTempClass(tmp, val.type);
        }
        return tmp;
    }

    private List<IR> collectArgs(NodeOptional opt, MethodContext argu) {
        List<IR> args = new ArrayList<>();
        if (opt != null && opt.present()) {
            ExpressionList list = (ExpressionList) opt.node;
            IR first = list.f0.accept(this, argu);
            args.add(first);
            if (list.f1 != null && list.f1.present()) {
                for (Enumeration<Node> e = list.f1.elements(); e.hasMoreElements(); ) {
                    Node node = e.nextElement();
                    IR next;
                    if (node instanceof ExpressionRest) {
                        next = ((ExpressionRest) node).f1.accept(this, argu);
                    } else {
                        next = node.accept(this, argu);
                    }
                    args.add(next);
                }
            }
        }
        return args;
    }

    private IR valueFromIdentifier(String name) {
        if (currentClass != null && currentMethod != null) {
            String temp = st.getTempForVariable(currentClass, currentMethod, name);
            if (temp != null) {
                String type = st.getVariableType(currentClass, currentMethod, name);
                if (isClassType(type)) {
                    st.recordTempClass(temp, type);
                }
                return new IR(temp, type);
            }
        }
        if (currentClass != null) {
            int offset = st.getFieldOffset(currentClass, name);
            if (offset >= 0) {
                String type = st.getFieldType(currentClass, name);
                String temp = newTemp();
                emit("HLOAD " + temp + " TEMP 0 " + offset);
                if (isClassType(type)) {
                    st.recordTempClass(temp, type);
                }
                return new IR(temp, type);
            }
        }
        return new IR(name, null);
    }

    public IR visit(PrimaryExpression n, MethodContext argu) {
        return n.f0.accept(this, argu);
    }
}
