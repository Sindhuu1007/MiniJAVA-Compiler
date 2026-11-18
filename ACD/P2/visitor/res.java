package visitor;

import syntaxtree.*;
import java.util.*;

public class res extends GJDepthFirst<String, Void> {
    private String currentClass = "";
    private String currentMethod = "";
    private int tempId = 0;

    private final StringBuilder sb = new StringBuilder();
    private final Map<String, String> methodReturn = new HashMap<>();
    private final Map<String, String> classMethodReturn = new HashMap<>();
    private final Set<String> ambiguousMethodReturns = new HashSet<>();
    private final Set<String> ambiguousClassMethodReturns = new HashSet<>();
    private final Map<String, Map<String, String>> classFieldTypes = new HashMap<>();
    private final Map<String, Map<String, String>> methodVarTypes = new HashMap<>();
    private Map<String, String> tempTypes = new HashMap<>();
    private StringBuilder body = null;
    private List<String> tempDecls = null;
    private List<String> varDecls = null;

    public res(Map<String, Integer> map) {}

    private void emit(String line) {
        StringBuilder target = (body != null) ? body : sb;
        target.append(line).append("\n");
    }

    private String addToDecl(String type) {
        String name = "_t" + (tempId++);
        if (tempDecls != null) {
            tempDecls.add(type + " " + name + ";");
        }
        if (type != null && !type.isEmpty()) {
            tempTypes.put(name, type);
        }
        return name;
    }

    private boolean isIdentifier(String s) {
        if (s == null || !s.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            return false;
        }
        return !("true".equals(s) || "false".equals(s) || "this".equals(s));
    }

    private String currentMethodKey() {
        if (currentClass.isEmpty() || currentMethod.isEmpty()) {
            return "";
        }
        return currentClass + "." + currentMethod;
    }

    private String getIDType(String id) {
        if(id == null || id.isEmpty()){
            return null;
        }
        if("this".equals(id)){
            return currentClass.isEmpty() ? null : currentClass;
        }
        if(tempTypes.containsKey(id)){
            return tempTypes.get(id);
        }

        String methodKey = currentMethodKey();
        if(!methodKey.isEmpty()){
            Map<String, String> vars = methodVarTypes.get(methodKey);
            if(vars != null && vars.containsKey(id)){
                return vars.get(id);
            }
        }

        Map<String, String> fields = classFieldTypes.get(currentClass);
        if(fields != null && fields.containsKey(id)){
            return fields.get(id);
        }
        return null;
    }

    private String inferReceiverType(String expr) {
        if(expr == null || expr.isEmpty()){
            return null;
        }
        if("this".equals(expr)){
            return currentClass.isEmpty() ? null : currentClass;
        }
        if(expr.startsWith("new ") && expr.endsWith("()")){
            return expr.substring(4, expr.length() - 2).trim();
        }
        if(isIdentifier(expr)){
            return getIDType(expr);
        }
        return null;
    }

    private String getMethodReturnType(String expr, String methodName) {
        String recvType = inferReceiverType(expr);
        if(recvType != null && !recvType.isEmpty()){
            String classKey = recvType + "." + methodName;
            if(!ambiguousClassMethodReturns.contains(classKey)){
                String ret = classMethodReturn.get(classKey);
                if(ret != null && !ret.isEmpty()){
                    return ret;
                }
            }
        }
        if(!ambiguousMethodReturns.contains(methodName)){
            String ret = methodReturn.get(methodName);
            if(ret != null && !ret.isEmpty()){
                return ret;
            }
        }
        if(expr != null && expr.contains(".") && !currentClass.isEmpty()){
            return currentClass;
        }
        return null;
    }

    private String getTempForExpr(String expr) {
        return getTempForExpr(expr, null);
    }

    private String getTempForExpr(String expr, String expectedType) {
        if (isIdentifier(expr)) {
            return expr;
        }
        String type = expectedType;
        if (type == null || type.isEmpty()) {
            if ("true".equals(expr) || "false".equals(expr) || expr.startsWith("!")) {
                type = "boolean";
            }
            else if ("this".equals(expr) && !currentClass.isEmpty()) {
                type = currentClass;
            }
            else if (expr.startsWith("new int[")) {
                type = "int[]";
            }
            else if (expr.endsWith(")") && expr.contains(".")) {
                int dot = expr.lastIndexOf('.');
                int paren = expr.indexOf('(', dot + 1);
                if (dot >= 0 && paren > dot) {
                    String expr = expr.substring(0, dot).trim();
                    String method = expr.substring(dot + 1, paren).trim();
                    String ret = getMethodReturnType(expr, method);
                    if (ret != null) {
                        type = ret;
                    }
                }
            }
            else if (expr.startsWith("new ") && expr.endsWith("()")) {
                type = expr.substring(4, expr.length() - 2).trim();
            }
        }
        if (type == null || type.isEmpty()) {
            type = "int";
        }
        String t = addToDecl(type);
        emit(t + " = " + expr + ";");
        return t;
    }

    private void registerMethodSignatures(NodeListOptional methods) {
        for (Enumeration<Node> e = methods.elements(); e.hasMoreElements();) {
            Node node = e.nextElement();
            if (!(node instanceof MethodDeclaration)) {
                continue;
            }
            MethodDeclaration m = (MethodDeclaration) node;
            String methodName = m.f2.f0.toString();
            String retType = m.f1.accept(this, null);
            String previousRet = methodReturn.get(methodName);
            if(previousRet == null){
                methodReturn.put(methodName, retType);
            } 
            else if (!previousRet.equals(retType)) {
                ambiguousMethodReturns.add(methodName);
                methodReturn.remove(methodName);
            }

            if(!currentClass.isEmpty()){
                String classKey = currentClass + "." + methodName;
                String previousClassRet = classMethodReturn.get(classKey);
                if(previousClassRet == null){
                    classMethodReturn.put(classKey, retType);
                }else if(!previousClassRet.equals(retType)){
                    ambiguousClassMethodReturns.add(classKey);
                    classMethodReturn.remove(classKey);
                }
            }
        }
    }

    public String visit(Goal n, Void argu) {
        n.f0.accept(this, null);
        n.f1.accept(this, null);
        return sb.toString();
    }

    public String visit(MainClass n, Void argu) {
        String className = n.f1.f0.toString();
        String argName = n.f11.f0.toString();

        sb.append("class ").append(className).append(" {\n");
        emit("public static void main(String[] " + argName + ") {");

        List<String> oldTemps = tempDecls;
        StringBuilder oldBody = body;
        int oldTempId = tempId;
        String oldMethod = currentMethod;

        tempId = 0;
        currentMethod = "main";
        tempTypes = new HashMap<>();
        tempDecls = new ArrayList<>();
        body = new StringBuilder();

        n.f14.accept(this, null);
        StringBuilder mainBody = body;
        body = null;
        for (String decl : tempDecls) {
            emit(decl);
        }
        sb.append(mainBody);

        tempDecls = oldTemps;
        body = oldBody;
        tempId = oldTempId;
        currentMethod = oldMethod;
        emit("}");
        sb.append("}\n");
        return "";
    }

    public String visit(ClassDeclaration n, Void argu) {
        currentClass = n.f1.f0.toString();
        sb.append("class ").append(currentClass).append(" {\n");
        classFieldTypes.computeIfAbsent(currentClass, k -> new HashMap<>());
        registerMethodSignatures(n.f4);
        n.f3.accept(this, null);
        n.f4.accept(this, null);
        sb.append("}\n");
        currentClass = "";
        return "";
    }

    public String visit(ClassExtendsDeclaration n, Void argu) {
        currentClass = n.f1.f0.toString();
        String baseClass = n.f3.f0.toString();
        sb.append("class ").append(currentClass).append(" extends ").append(baseClass).append(" {\n");
        classFieldTypes.computeIfAbsent(currentClass, k -> new HashMap<>());
        registerMethodSignatures(n.f6);
        n.f5.accept(this, null);
        n.f6.accept(this, null);
        sb.append("}\n");
        currentClass = "";
        return "";
    }

    public String visit(VarDeclaration n, Void argu) {
        return n.f0.accept(this, null);
    }

    public String visit(SimpleVarDeclaration n, Void argu) {
        String type = n.f0.accept(this, null);
        String name = n.f1.f0.toString();
        String decl = type + " " + name + ";";
        if(!currentMethod.isEmpty()){
            methodVarTypes.computeIfAbsent(currentMethodKey(), k -> new HashMap<>()).put(name, type);
        }else if(!currentClass.isEmpty()){
            classFieldTypes.computeIfAbsent(currentClass, k -> new HashMap<>()).put(name, type);
        }

        if (varDecls != null) {
            varDecls.add(decl);
        } else {
            emit(decl);
        }
        return "";
    }

    public String visit(FinalVarDeclaration n, Void argu) {
        String type = n.f1.accept(this, null);
        String name = n.f2.f0.toString();
        String rhs = n.f4.accept(this, null);
        String decl = type + " " + name + ";";
        if(!currentMethod.isEmpty()){
            methodVarTypes.computeIfAbsent(currentMethodKey(), k -> new HashMap<>()).put(name, type);
        }

        if (varDecls != null) {
            varDecls.add(decl);
            emit(name + " = " + rhs + ";");
        } else {
            emit(decl);
        }
        return "";
    }

    public String visit(MethodDeclaration n, Void argu) {
        String oldMethod = currentMethod;
        Map<String, String> oldTempTypes = tempTypes;
        currentMethod = n.f2.f0.toString();

        int oldTempId = tempId;
        tempId = 0;

        List<String> oldTemps = tempDecls;
        List<String> oldVars = varDecls;
        StringBuilder oldBody = body;

        tempDecls = new ArrayList<>();
        varDecls = new ArrayList<>();
        tempTypes = new HashMap<>();
        body = new StringBuilder();
        methodVarTypes.put(currentMethodKey(), new HashMap<>());
        String retType = n.f1.accept(this, null);
        String params = "";
        if (n.f4.present()) {
            params = n.f4.node.accept(this, null);
        }

        n.f7.accept(this, null);
        n.f8.accept(this, null);
        String retExpr = n.f10.accept(this, null);
        String retId = getTempForExpr(retExpr, retType);
        emit("return " + retId + ";");

        StringBuilder methodBody = body;
        body = null;
        sb.append("\n");
        emit("public " + retType + " " + currentMethod + "(" + params + ") {");
        for (String decl : varDecls) {
            emit(decl);
        }
        for (String decl : tempDecls) {
            emit(decl);
        }
        sb.append(methodBody);
        emit("}");

        tempDecls = oldTemps;
        varDecls = oldVars;
        body = oldBody;
        tempId = oldTempId;
        tempTypes = oldTempTypes;
        currentMethod = oldMethod;
        return "";
    }

    public String visit(FormalParameterList n, Void argu) {
        StringBuilder p = new StringBuilder();
        p.append(n.f0.accept(this, null));
        if (n.f1.present()) {
            for (Enumeration<Node> e = n.f1.elements(); e.hasMoreElements();) {
                p.append(e.nextElement().accept(this, null));
            }
        }
        return p.toString();
    }

    public String visit(FormalParameter n, Void argu) {
        String type = n.f0.accept(this, null);
        String name = n.f1.f0.toString();
        if (!currentMethod.isEmpty()) {
            methodVarTypes.computeIfAbsent(currentMethodKey(), k -> new HashMap<>()).put(name, type);
        }
        return type + " " + name;
    }

    public String visit(FormalParameterRest n, Void argu) {
        return ", " + n.f1.accept(this, null);
    }

    public String visit(Type n, Void argu) {
        return n.f0.accept(this, null);
    }

    public String visit(ArrayType n, Void argu) {
        return "int[]";
    }

    public String visit(BooleanType n, Void argu) {
        return "boolean";
    }

    public String visit(IntegerType n, Void argu) {
        return "int";
    }

    public String visit(Statement n, Void argu) {
        return n.f0.accept(this, null);
    }

    public String visit(Block n, Void argu) {
        emit("{");
        n.f1.accept(this, null);
        emit("}");
        return "";
    }

    public String visit(AssignmentStatement n, Void argu) {
        String id = n.f0.f0.toString();
        String e = n.f2.accept(this, null);
        emit(id + " = " + e + ";");
        return "";
    }

    public String visit(ArrayAssignmentStatement n, Void argu) {
        String arr = n.f0.f0.toString();
        String idx = getTempForExpr(n.f2.accept(this, null), "int");
        String val = getTempForExpr(n.f5.accept(this, null), "int");
        emit(arr + "[" + idx + "] = " + val + ";");
        return "";
    }

    public String visit(FieldAssignmentStatement n, Void argu) {
        String obj = getTempForExpr(n.f0.accept(this, null), currentClass);
        String field = n.f2.f0.toString();
        String rhs = n.f4.accept(this, null);
        String val = getTempForExpr(rhs, null);
        emit(obj + "." + field + " = " + val + ";");
        return "";
    }

    public String visit(IfStatement n, Void argu) {
        String condExpr = n.f2.accept(this, null);
        String cond = getTempForExpr(condExpr, "boolean");
        emit("if (" + cond + ") {");
        n.f4.accept(this, null);
        emit("} else {");
        n.f6.accept(this, null);
        emit("}");
        return "";
    }

    public String visit(WhileStatement n, Void argu) {
        String condExpr = n.f2.accept(this, null);
        String cond = getTempForExpr(condExpr, "boolean");
        emit("while (" + cond + ") {");
        n.f4.accept(this, null);
        String nextCondExpr = n.f2.accept(this, null);
        String nextCond = getTempForExpr(nextCondExpr, "boolean");
        emit(cond + " = " + nextCond + ";");
        emit("}");
        return "";
    }

    public String visit(ForStatement n, Void argu) {
        String var = n.f2.f0.toString();
        String init = n.f4.accept(this, null);
        emit(var + " = " + init + ";");
        String condExpr = n.f6.accept(this, null);
        String cond = getTempForExpr(condExpr, "boolean");
        emit("while (" + cond + ") {");
        n.f12.accept(this, null);
        String updVar = n.f8.f0.toString();
        String upd = n.f10.accept(this, null);
        emit(updVar + " = " + upd + ";");
        String nextCondExpr = n.f6.accept(this, null);
        String nextCond = getTempForExpr(nextCondExpr, "boolean");
        emit(cond + " = " + nextCond + ";");
        emit("}");
        return "";
    }


    public String visit(PrintStatement n, Void argu) {
        String e = n.f2.accept(this, null);
        String id = getTempForExpr(e, "int");
        emit("System.out.println(" + id + ");");
        return "";
    }

    public String visit(Expression n, Void argu) {
        return n.f0.accept(this, null);
    }

    public String visit(AndExpression n, Void argu) {
        String l = getTempForExpr(n.f0.accept(this, null), "boolean");
        String r = getTempForExpr(n.f2.accept(this, null), "boolean");
        String t = addToDecl("boolean");
        emit(t + " = false;");
        emit("if (" + l + ") {");
        emit(t + " = " + r + ";");
        emit("} else {");
        emit(t + " = false;");
        emit("}");
        return t;
    }

    public String visit(ORExpression n, Void argu) {
        String l = getTempForExpr(n.f0.accept(this, null), "boolean");
        String r = getTempForExpr(n.f2.accept(this, null), "boolean");
        String t = addToDecl("boolean");
        emit(t + " = true;");
        emit("if (" + l + ") {");
        emit(t + " = true;");
        emit("} else {");
        emit(t + " = " + r + ";");
        emit("}");
        return t;
    }

    public String visit(CompareExpression n, Void argu) {
        String l = getTempForExpr(n.f0.accept(this, null), "int");
        String r = getTempForExpr(n.f2.accept(this, null), "int");
        String t = addToDecl("boolean");
        emit(t + " = " + l + " < " + r + ";");
        return t;
    }

    public String visit(PlusExpression n, Void argu) {
        String l = getTempForExpr(n.f0.accept(this, null), "int");
        String r = getTempForExpr(n.f2.accept(this, null), "int");
        String t = addToDecl("int");
        emit(t + " = " + l + " + " + r + ";");
        return t;
    }

    public String visit(MinusExpression n, Void argu) {
        String l = getTempForExpr(n.f0.accept(this, null), "int");
        String r = getTempForExpr(n.f2.accept(this, null), "int");
        String t = addToDecl("int");
        emit(t + " = " + l + " - " + r + ";");
        return t;
    }

    public String visit(TimesExpression n, Void argu) {
        String l = getTempForExpr(n.f0.accept(this, null), "int");
        String r = getTempForExpr(n.f2.accept(this, null), "int");
        String t = addToDecl("int");
        emit(t + " = " + l + " * " + r + ";");
        return t;
    }

    public String visit(ArrayLookup n, Void argu) {
        String arr = getTempForExpr(n.f0.accept(this, null), "int");
        String idx = getTempForExpr(n.f2.accept(this, null), "int");
        String t = addToDecl("int");
        emit(t + " = " + arr + "[" + idx + "]; ");
        return t;
    }

    public String visit(ArrayLength n, Void argu) {
        String arr = getTempForExpr(n.f0.accept(this, null), "int");
        String t = addToDecl("int");
        emit(t + " = " + arr + ".length;");
        return t;
    }

    public String visit(MessageSend n, Void argu) {
        String obj = n.f0.accept(this, argu);
        if (!isIdentifier(obj) && !obj.startsWith("new ") && !"this".equals(obj) && !currentClass.isEmpty()) {
            obj = getTempForExpr(obj, currentClass);
        }
        String method = n.f2.f0.tokenImage;
        String args = "";
        if (n.f4.present()) {
            args = n.f4.node.accept(this, argu);
        }
        return obj + "." + method + "(" + args + ")";
    }

    public String visit(ExpressionList n, Void argu) {
        StringBuilder args = new StringBuilder();
        String first = n.f0.accept(this, null);
        args.append(getTempForExpr(first));
        if (n.f1.present()) {
            for (Enumeration<Node> e = n.f1.elements(); e.hasMoreElements();) {
                args.append(e.nextElement().accept(this, null));
            }
        }
        return args.toString();
    }

    public String visit(ExpressionRest n, Void argu) {
        String e = n.f1.accept(this, null);
        return ", " + getTempForExpr(e);
    }

    public String visit(PrimaryExpression n, Void argu) {
        return n.f0.accept(this, null);
    }

    public String visit(IntegerLiteral n, Void argu) {
        return n.f0.toString();
    }

    public String visit(TrueLiteral n, Void argu) {
        return "true";
    }

    public String visit(FalseLiteral n, Void argu) {
        return "false";
    }

    public String visit(Identifier n, Void argu) {
        return n.f0.toString();
    }

    public String visit(ThisExpression n, Void argu) {
        return "this";
    }

    public String visit(ArrayAllocationExpression n, Void argu) {
        String size = getTempForExpr(n.f3.accept(this, null), "int");
        return "new int[" + size + "]";
    }

    public String visit(AllocationExpression n, Void argu) {
        return "new " + n.f1.f0.toString() + "()";
    }

    public String visit(NotExpression n, Void argu) {
        String v = getTempForExpr(n.f1.accept(this, null), "boolean");
        return ("!" + v);
    }

    public String visit(BracketExpression n, Void argu) {
        return n.f1.accept(this, null);
    }
}