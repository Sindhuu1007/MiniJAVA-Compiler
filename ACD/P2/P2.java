import java.util.*;

import syntaxtree.*;

public class P2 {
    public static void main(String[] args) {
        try {
            BuritoJavaParser parser = new BuritoJavaParser(System.in);
            Goal program = parser.Goal();

            SymbolTable symbols = SymbolTable.build(program);
            Translator translator = new Translator(symbols);
            String out = translator.translate(program);
            System.out.print(out);
        } catch (ParseException e) {
            throw new RuntimeException("Parse error: " + e.getMessage(), e);
        }
    }

    private static final class SymbolTable {
        private final Map<String, ClassInfo> classes;

        private SymbolTable(Map<String, ClassInfo> classes) {
            this.classes = classes;
        }

        static SymbolTable build(Goal goal) {
            Map<String, ClassInfo> classes = new LinkedHashMap<String, ClassInfo>();

            String mainClassName = tokenImage(goal.f0.f1);
            classes.put(mainClassName, new ClassInfo(mainClassName, null));

            for (Node n : nodes(goal.f1)) {
                TypeDeclaration td = (TypeDeclaration) n;
                NodeChoice ch = td.f0;
                if (ch.which == 0) {
                    ClassDeclaration cd = (ClassDeclaration) ch.choice;
                    String className = tokenImage(cd.f1);
                    ClassInfo ci = new ClassInfo(className, null);
                    collectClassMembers(ci, cd.f3, cd.f4);
                    classes.put(className, ci);
                } else {
                    ClassExtendsDeclaration cd = (ClassExtendsDeclaration) ch.choice;
                    String className = tokenImage(cd.f1);
                    String parentName = tokenImage(cd.f3);
                    ClassInfo ci = new ClassInfo(className, parentName);
                    collectClassMembers(ci, cd.f5, cd.f6);
                    classes.put(className, ci);
                }
            }

            return new SymbolTable(classes);
        }

        private static void collectClassMembers(ClassInfo ci, NodeListOptional fieldDecls, NodeListOptional methods) {
            for (Node n : nodes(fieldDecls)) {
                SimpleVarDeclaration vd = (SimpleVarDeclaration) n;
                ci.fields.put(tokenImage(vd.f1), typeToString(vd.f0));
            }

            for (Node n : nodes(methods)) {
                MethodDeclaration md = (MethodDeclaration) n;
                String methodName = tokenImage(md.f2);
                MethodInfo mi = new MethodInfo(methodName, typeToString(md.f1));

                if (md.f4.present()) {
                    FormalParameterList params = (FormalParameterList) md.f4.node;
                    collectParams(mi, params);
                }

                for (Node v : nodes(md.f7)) {
                    VarDeclaration vd = (VarDeclaration) v;
                    NodeChoice ch = vd.f0;
                    if (ch.which == 0) {
                        SimpleVarDeclaration sv = (SimpleVarDeclaration) ch.choice;
                        mi.locals.put(tokenImage(sv.f1), typeToString(sv.f0));
                    } else {
                        FinalVarDeclaration fv = (FinalVarDeclaration) ch.choice;
                        mi.locals.put(tokenImage(fv.f2), typeToString(fv.f1));
                    }
                }

                ci.methods.put(methodName, mi);
            }
        }

        private static void collectParams(MethodInfo mi, FormalParameterList params) {
            FormalParameter first = params.f0;
            mi.params.put(tokenImage(first.f1), typeToString(first.f0));
            for (Node n : nodes(params.f1)) {
                FormalParameterRest rest = (FormalParameterRest) n;
                FormalParameter fp = rest.f1;
                mi.params.put(tokenImage(fp.f1), typeToString(fp.f0));
            }
        }

        String lookupVarType(String className, String methodName, String var) {
            if ("this".equals(var)) {
                return className;
            }
            ClassInfo ci = classes.get(className);
            if (ci == null) {
                return "int";
            }
            if (methodName != null) {
                MethodInfo mi = ci.methods.get(methodName);
                if (mi != null) {
                    String t = mi.locals.get(var);
                    if (t != null) {
                        return t;
                    }
                    t = mi.params.get(var);
                    if (t != null) {
                        return t;
                    }
                }
            }

            String current = className;
            while (current != null) {
                ClassInfo curClass = classes.get(current);
                if (curClass == null) {
                    break;
                }
                String t = curClass.fields.get(var);
                if (t != null) {
                    return t;
                }
                current = curClass.parent;
            }
            return "int";
        }

        String lookupMethodReturnType(String receiverType, String methodName) {
            String current = receiverType;
            while (current != null) {
                ClassInfo ci = classes.get(current);
                if (ci == null) {
                    break;
                }
                MethodInfo mi = ci.methods.get(methodName);
                if (mi != null) {
                    return mi.returnType;
                }
                current = ci.parent;
            }
            return "int";
        }
    }

    private static final class ClassInfo {
        private final String name;
        private final String parent;
        private final Map<String, String> fields = new LinkedHashMap<String, String>();
        private final Map<String, MethodInfo> methods = new LinkedHashMap<String, MethodInfo>();

        private ClassInfo(String name, String parent) {
            this.name = name;
            this.parent = parent;
        }
    }

    private static final class MethodInfo {
        private final String name;
        private final String returnType;
        private final Map<String, String> params = new LinkedHashMap<String, String>();
        private final Map<String, String> locals = new LinkedHashMap<String, String>();

        private MethodInfo(String name, String returnType) {
            this.name = name;
            this.returnType = returnType;
        }
    }

    private static final class ExprResult {
        private final String value;
        private final String type;

        private ExprResult(String value, String type) {
            this.value = value;
            this.type = type;
        }
    }

    private static final class MethodContext {
        private final SymbolTable symbols;
        private final String className;
        private final String methodName;
        private final Set<String> originalLocals = new LinkedHashSet<String>();
        private final Map<String, String> tempTypes = new LinkedHashMap<String, String>();
        private final List<String> initStatements = new ArrayList<String>();
        private int tempId = 0;

        private MethodContext(SymbolTable symbols, String className, String methodName) {
            this.symbols = symbols;
            this.className = className;
            this.methodName = methodName;
        }

        private String freshTemp(String type) {
            String t;
            do {
                t = "_t" + tempId;
                tempId++;
            } while (originalLocals.contains(t) || tempTypes.containsKey(t));
            tempTypes.put(t, type);
            return t;
        }

        private String lookupVarType(String var) {
            if (tempTypes.containsKey(var)) {
                return tempTypes.get(var);
            }
            return symbols.lookupVarType(className, methodName, var);
        }
    }

    private static final class Translator {
        private final SymbolTable symbols;

        private Translator(SymbolTable symbols) {
            this.symbols = symbols;
        }

        String translate(Goal goal) {
            StringBuilder sb = new StringBuilder();
            emitMainClass(goal.f0, sb);

            for (Node n : nodes(goal.f1)) {
                sb.append('\n');
                TypeDeclaration td = (TypeDeclaration) n;
                NodeChoice ch = td.f0;
                if (ch.which == 0) {
                    emitClassDeclaration((ClassDeclaration) ch.choice, sb);
                } else {
                    emitClassExtendsDeclaration((ClassExtendsDeclaration) ch.choice, sb);
                }
            }
            return sb.toString();
        }

        private void emitMainClass(MainClass n, StringBuilder sb) {
            String className = tokenImage(n.f1);
            MethodContext ctx = new MethodContext(symbols, className, null);

            List<String> bodyStatements = new ArrayList<String>();
            emitPrintStatement(n.f14, ctx, bodyStatements, "        ");

            sb.append("class ").append(className).append(" {\n");
            sb.append("    public static void main(String[] ").append(tokenImage(n.f11)).append(") {\n");
            emitVarDecls(ctx.tempTypes, "        ", sb);
            for (String st : bodyStatements) {
                sb.append(st);
            }
            sb.append("    }\n");
            sb.append("}\n");
        }

        private void emitClassDeclaration(ClassDeclaration n, StringBuilder sb) {
            String className = tokenImage(n.f1);
            sb.append("class ").append(className).append(" {\n");

            for (Node node : nodes(n.f3)) {
                SimpleVarDeclaration vd = (SimpleVarDeclaration) node;
                sb.append("    ").append(typeToString(vd.f0)).append(" ").append(tokenImage(vd.f1)).append(";\n");
            }

            for (Node node : nodes(n.f4)) {
                emitMethodDeclaration(className, (MethodDeclaration) node, sb);
            }
            sb.append("}\n");
        }

        private void emitClassExtendsDeclaration(ClassExtendsDeclaration n, StringBuilder sb) {
            String className = tokenImage(n.f1);
            sb.append("class ").append(className).append(" extends ").append(tokenImage(n.f3)).append(" {\n");

            for (Node node : nodes(n.f5)) {
                SimpleVarDeclaration vd = (SimpleVarDeclaration) node;
                sb.append("    ").append(typeToString(vd.f0)).append(" ").append(tokenImage(vd.f1)).append(";\n");
            }

            for (Node node : nodes(n.f6)) {
                emitMethodDeclaration(className, (MethodDeclaration) node, sb);
            }
            sb.append("}\n");
        }

        private void emitMethodDeclaration(String className, MethodDeclaration n, StringBuilder sb) {
            String methodName = tokenImage(n.f2);
            MethodContext ctx = new MethodContext(symbols, className, methodName);

            List<String> declaredLocalLines = new ArrayList<String>();
            collectMethodLocals(n.f7, ctx, declaredLocalLines);

            List<String> bodyStatements = new ArrayList<String>();
            bodyStatements.addAll(ctx.initStatements);
            for (Node node : nodes(n.f8)) {
                emitStatement((Statement) node, ctx, bodyStatements, "        ");
            }
            ExprResult ret = lowerExpression(n.f10, ctx, bodyStatements, "        ");

            sb.append("\n");
            sb.append("    public ").append(typeToString(n.f1)).append(" ").append(methodName).append("(");
            emitFormalParams(n.f4, sb);
            sb.append(") {\n");

            for (String localLine : declaredLocalLines) {
                sb.append(localLine);
            }
            emitVarDecls(ctx.tempTypes, "        ", sb);

            for (String st : bodyStatements) {
                sb.append(st);
            }
            sb.append("        return ").append(ret.value).append(";\n");
            sb.append("    }\n");
        }

        private void collectMethodLocals(NodeListOptional varDecls, MethodContext ctx, List<String> outDecls) {
            for (Node node : nodes(varDecls)) {
                VarDeclaration vd = (VarDeclaration) node;
                NodeChoice ch = vd.f0;
                if (ch.which == 0) {
                    SimpleVarDeclaration sv = (SimpleVarDeclaration) ch.choice;
                    String name = tokenImage(sv.f1);
                    String type = typeToString(sv.f0);
                    ctx.originalLocals.add(name);
                    outDecls.add("        " + type + " " + name + ";\n");
                } else {
                    FinalVarDeclaration fv = (FinalVarDeclaration) ch.choice;
                    String name = tokenImage(fv.f2);
                    String type = typeToString(fv.f1);
                    ctx.originalLocals.add(name);
                    outDecls.add("        " + type + " " + name + ";\n");

                    ExprResult init = lowerExpression(fv.f4, ctx, ctx.initStatements, "        ");
                    ctx.initStatements.add("        " + name + " = " + init.value + ";\n");
                }
            }
        }

        private void emitFormalParams(NodeOptional paramsOpt, StringBuilder sb) {
            if (!paramsOpt.present()) {
                return;
            }
            FormalParameterList params = (FormalParameterList) paramsOpt.node;
            FormalParameter first = params.f0;
            sb.append(typeToString(first.f0)).append(" ").append(tokenImage(first.f1));
            for (Node node : nodes(params.f1)) {
                FormalParameterRest rest = (FormalParameterRest) node;
                sb.append(", ").append(typeToString(rest.f1.f0)).append(" ").append(tokenImage(rest.f1.f1));
            }
        }

        private void emitStatement(Statement n, MethodContext ctx, List<String> out, String indent) {
            NodeChoice ch = n.f0;
            switch (ch.which) {
                case 0:
                    emitBlock((Block) ch.choice, ctx, out, indent);
                    break;
                case 1:
                    emitAssignment((AssignmentStatement) ch.choice, ctx, out, indent);
                    break;
                case 2:
                    emitArrayAssignment((ArrayAssignmentStatement) ch.choice, ctx, out, indent);
                    break;
                case 3:
                    emitFieldAssignment((FieldAssignmentStatement) ch.choice, ctx, out, indent);
                    break;
                case 4:
                    emitIf((IfStatement) ch.choice, ctx, out, indent);
                    break;
                case 5:
                    emitWhile((WhileStatement) ch.choice, ctx, out, indent);
                    break;
                case 6:
                    emitForAsWhile((ForStatement) ch.choice, ctx, out, indent);
                    break;
                case 7:
                    emitPrintStatement((PrintStatement) ch.choice, ctx, out, indent);
                    break;
                default:
                    throw new IllegalStateException("Unknown statement choice: " + ch.which);
            }
        }

        private void emitBlock(Block n, MethodContext ctx, List<String> out, String indent) {
            out.add(indent + "{\n");
            for (Node node : nodes(n.f1)) {
                emitStatement((Statement) node, ctx, out, indent + "    ");
            }
            out.add(indent + "}\n");
        }

        private void emitAssignment(AssignmentStatement n, MethodContext ctx, List<String> out, String indent) {
            ExprResult rhs = lowerExpression(n.f2, ctx, out, indent);
            out.add(indent + tokenImage(n.f0) + " = " + rhs.value + ";\n");
        }

        private void emitArrayAssignment(ArrayAssignmentStatement n, MethodContext ctx, List<String> out, String indent) {
            ExprResult idx = lowerExpression(n.f2, ctx, out, indent);
            ExprResult rhs = lowerExpression(n.f5, ctx, out, indent);
            out.add(indent + tokenImage(n.f0) + "[" + idx.value + "] = " + rhs.value + ";\n");
        }

        private void emitFieldAssignment(FieldAssignmentStatement n, MethodContext ctx, List<String> out, String indent) {
            ExprResult base = lowerExpression(n.f0, ctx, out, indent);
            ExprResult rhs = lowerExpression(n.f4, ctx, out, indent);
            out.add(indent + base.value + "." + tokenImage(n.f2) + " = " + rhs.value + ";\n");
        }

        private void emitIf(IfStatement n, MethodContext ctx, List<String> out, String indent) {
            ExprResult cond = lowerExpression(n.f2, ctx, out, indent);
            List<String> thenLines = new ArrayList<String>();
            List<String> elseLines = new ArrayList<String>();
            emitStatement(n.f4, ctx, thenLines, indent + "        ");
            emitStatement(n.f6, ctx, elseLines, indent + "        ");

            out.add(indent + "if (" + cond.value + ")\n");
            out.add(indent + "    {\n");
            out.addAll(thenLines);
            out.add(indent + "    }\n");
            out.add(indent + "else\n");
            out.add(indent + "    {\n");
            out.addAll(elseLines);
            out.add(indent + "    }\n");
        }

        private void emitWhile(WhileStatement n, MethodContext ctx, List<String> out, String indent) {
            out.add(indent + "{\n");
            ExprResult cond = lowerExpression(n.f2, ctx, out, indent + "    ");
            String condVar = cond.value;
            out.add(indent + "    while (" + condVar + ")\n");
            out.add(indent + "        {\n");
            emitStatement(n.f4, ctx, out, indent + "            ");
            ExprResult nextCond = lowerExpression(n.f2, ctx, out, indent + "            ");
            out.add(indent + "            " + condVar + " = " + nextCond.value + ";\n");
            out.add(indent + "        }\n");
            out.add(indent + "}\n");
        }

        private void emitForAsWhile(ForStatement n, MethodContext ctx, List<String> out, String indent) {
            out.add(indent + "{\n");
            List<String> scoped = new ArrayList<String>();

            ExprResult init = lowerExpression(n.f4, ctx, scoped, indent + "    ");
            scoped.add(indent + "    " + tokenImage(n.f2) + " = " + init.value + ";\n");

            ExprResult cond = lowerExpression(n.f6, ctx, scoped, indent + "    ");
            String condVar = cond.value;
            scoped.add(indent + "    while (" + condVar + ")\n");
            scoped.add(indent + "        {\n");
            emitStatement(n.f12, ctx, scoped, indent + "            ");
            ExprResult upd = lowerExpression(n.f10, ctx, scoped, indent + "            ");
            scoped.add(indent + "            " + tokenImage(n.f8) + " = " + upd.value + ";\n");
            ExprResult nextCond = lowerExpression(n.f6, ctx, scoped, indent + "            ");
            scoped.add(indent + "            " + condVar + " = " + nextCond.value + ";\n");
            scoped.add(indent + "        }\n");

            out.addAll(scoped);
            out.add(indent + "}\n");
        }

        private void emitPrintStatement(PrintStatement n, MethodContext ctx, List<String> out, String indent) {
            ExprResult v = lowerExpression(n.f2, ctx, out, indent);
            out.add(indent + "System.out.println(" + v.value + ");\n");
        }

        private ExprResult lowerExpression(Expression n, MethodContext ctx, List<String> out, String indent) {
            NodeChoice ch = n.f0;
            switch (ch.which) {
                case 0:
                    return lowerOr((ORExpression) ch.choice, ctx, out, indent);
                case 1:
                    return lowerAnd((AndExpression) ch.choice, ctx, out, indent);
                case 2:
                    return lowerCompare((CompareExpression) ch.choice, ctx, out, indent);
                case 3:
                    return lowerPlus((PlusExpression) ch.choice, ctx, out, indent);
                case 4:
                    return lowerMinus((MinusExpression) ch.choice, ctx, out, indent);
                case 5:
                    return lowerTimes((TimesExpression) ch.choice, ctx, out, indent);
                case 6:
                    return lowerArrayLookup((ArrayLookup) ch.choice, ctx, out, indent);
                case 7:
                    return lowerArrayLength((ArrayLength) ch.choice, ctx, out, indent);
                case 8:
                    return lowerMessageSend((MessageSend) ch.choice, ctx, out, indent);
                case 9:
                    return lowerPrimary((PrimaryExpression) ch.choice, ctx, out, indent);
                default:
                    throw new IllegalStateException("Unknown expression choice: " + ch.which);
            }
        }

        private ExprResult lowerOr(ORExpression n, MethodContext ctx, List<String> out, String indent) {
            ExprResult l = lowerPrimary(n.f0, ctx, out, indent);
            ExprResult r = lowerPrimary(n.f2, ctx, out, indent);
            String t = ctx.freshTemp("boolean");
            out.add(indent + t + " = " + l.value + ";\n");
            out.add(indent + "if (" + t + ")\n");
            out.add(indent + "    " + t + " = true;\n");
            out.add(indent + "else\n");
            out.add(indent + "    " + t + " = " + r.value + ";\n");
            return new ExprResult(t, "boolean");
        }

        private ExprResult lowerAnd(AndExpression n, MethodContext ctx, List<String> out, String indent) {
            ExprResult l = lowerPrimary(n.f0, ctx, out, indent);
            ExprResult r = lowerPrimary(n.f2, ctx, out, indent);
            String t = ctx.freshTemp("boolean");
            out.add(indent + t + " = " + l.value + ";\n");
            out.add(indent + "if (" + t + ")\n");
            out.add(indent + "    " + t + " = " + r.value + ";\n");
            out.add(indent + "else\n");
            out.add(indent + "    " + t + " = false;\n");
            return new ExprResult(t, "boolean");
        }

        private ExprResult lowerCompare(CompareExpression n, MethodContext ctx, List<String> out, String indent) {
            ExprResult l = lowerPrimary(n.f0, ctx, out, indent);
            ExprResult r = lowerPrimary(n.f2, ctx, out, indent);
            String t = ctx.freshTemp("boolean");
            out.add(indent + t + " = " + l.value + " < " + r.value + ";\n");
            return new ExprResult(t, "boolean");
        }

        private ExprResult lowerPlus(PlusExpression n, MethodContext ctx, List<String> out, String indent) {
            ExprResult l = lowerPrimary(n.f0, ctx, out, indent);
            ExprResult r = lowerPrimary(n.f2, ctx, out, indent);
            String t = ctx.freshTemp("int");
            out.add(indent + t + " = " + l.value + " + " + r.value + ";\n");
            return new ExprResult(t, "int");
        }

        private ExprResult lowerMinus(MinusExpression n, MethodContext ctx, List<String> out, String indent) {
            ExprResult l = lowerPrimary(n.f0, ctx, out, indent);
            ExprResult r = lowerPrimary(n.f2, ctx, out, indent);
            String t = ctx.freshTemp("int");
            out.add(indent + t + " = " + l.value + " - " + r.value + ";\n");
            return new ExprResult(t, "int");
        }

        private ExprResult lowerTimes(TimesExpression n, MethodContext ctx, List<String> out, String indent) {
            ExprResult l = lowerPrimary(n.f0, ctx, out, indent);
            ExprResult r = lowerPrimary(n.f2, ctx, out, indent);
            String t = ctx.freshTemp("int");
            out.add(indent + t + " = " + l.value + " * " + r.value + ";\n");
            return new ExprResult(t, "int");
        }

        private ExprResult lowerArrayLookup(ArrayLookup n, MethodContext ctx, List<String> out, String indent) {
            ExprResult arr = lowerPrimary(n.f0, ctx, out, indent);
            ExprResult idx = lowerPrimary(n.f2, ctx, out, indent);
            String t = ctx.freshTemp("int");
            out.add(indent + t + " = " + arr.value + "[" + idx.value + "];\n");
            return new ExprResult(t, "int");
        }

        private ExprResult lowerArrayLength(ArrayLength n, MethodContext ctx, List<String> out, String indent) {
            ExprResult arr = lowerPrimary(n.f0, ctx, out, indent);
            String t = ctx.freshTemp("int");
            out.add(indent + t + " = " + arr.value + ".length;\n");
            return new ExprResult(t, "int");
        }

        private ExprResult lowerMessageSend(MessageSend n, MethodContext ctx, List<String> out, String indent) {
            ExprResult recv = lowerPrimary(n.f0, ctx, out, indent);
            String method = tokenImage(n.f2);
            List<String> args = new ArrayList<String>();

            if (n.f4.present()) {
                ExpressionList list = (ExpressionList) n.f4.node;
                ExprResult first = lowerExpression(list.f0, ctx, out, indent);
                args.add(first.value);
                for (Node node : nodes(list.f1)) {
                    ExpressionRest rest = (ExpressionRest) node;
                    ExprResult e = lowerExpression(rest.f1, ctx, out, indent);
                    args.add(e.value);
                }
            }

            String retType = symbols.lookupMethodReturnType(recv.type, method);
            String t = ctx.freshTemp(retType);
            out.add(indent + t + " = " + recv.value + "." + method + "(" + join(args, ", ") + ");\n");
            return new ExprResult(t, retType);
        }

        private ExprResult lowerPrimary(PrimaryExpression n, MethodContext ctx, List<String> out, String indent) {
            NodeChoice ch = n.f0;
            switch (ch.which) {
                case 0:
                    return lowerIntegerLiteral((IntegerLiteral) ch.choice, ctx, out, indent);
                case 1:
                    return lowerTrueLiteral(ctx, out, indent);
                case 2:
                    return lowerFalseLiteral(ctx, out, indent);
                case 3:
                    String id = tokenImage((Identifier) ch.choice);
                    return new ExprResult(id, ctx.lookupVarType(id));
                case 4:
                    return lowerThis((ThisExpression) ch.choice, ctx, out, indent);
                case 5:
                    return lowerArrayAlloc((ArrayAllocationExpression) ch.choice, ctx, out, indent);
                case 6:
                    return lowerAlloc((AllocationExpression) ch.choice, ctx, out, indent);
                case 7:
                    return lowerNot((NotExpression) ch.choice, ctx, out, indent);
                case 8:
                    return lowerBracket((BracketExpression) ch.choice, ctx, out, indent);
                default:
                    throw new IllegalStateException("Unknown primary choice: " + ch.which);
            }
        }

        private ExprResult lowerIntegerLiteral(IntegerLiteral n, MethodContext ctx, List<String> out, String indent) {
            String t = ctx.freshTemp("int");
            out.add(indent + t + " = " + n.f0.tokenImage + ";\n");
            return new ExprResult(t, "int");
        }

        private ExprResult lowerTrueLiteral(MethodContext ctx, List<String> out, String indent) {
            String t = ctx.freshTemp("boolean");
            out.add(indent + t + " = true;\n");
            return new ExprResult(t, "boolean");
        }

        private ExprResult lowerFalseLiteral(MethodContext ctx, List<String> out, String indent) {
            String t = ctx.freshTemp("boolean");
            out.add(indent + t + " = false;\n");
            return new ExprResult(t, "boolean");
        }

        private ExprResult lowerThis(ThisExpression n, MethodContext ctx, List<String> out, String indent) {
            String t = ctx.freshTemp(ctx.className);
            out.add(indent + t + " = this;\n");
            return new ExprResult(t, ctx.className);
        }

        private ExprResult lowerArrayAlloc(ArrayAllocationExpression n, MethodContext ctx, List<String> out, String indent) {
            ExprResult size = lowerExpression(n.f3, ctx, out, indent);
            String t = ctx.freshTemp("int[]");
            out.add(indent + t + " = new int[" + size.value + "];\n");
            return new ExprResult(t, "int[]");
        }

        private ExprResult lowerAlloc(AllocationExpression n, MethodContext ctx, List<String> out, String indent) {
            String className = tokenImage(n.f1);
            String t = ctx.freshTemp(className);
            out.add(indent + t + " = new " + className + "();\n");
            return new ExprResult(t, className);
        }

        private ExprResult lowerNot(NotExpression n, MethodContext ctx, List<String> out, String indent) {
            NodeChoice ch = n.f1;
            ExprResult arg;
            if (ch.which == 0) {
                arg = lowerMessageSend((MessageSend) ch.choice, ctx, out, indent);
            } else {
                arg = lowerPrimary((PrimaryExpression) ch.choice, ctx, out, indent);
            }
            String t = ctx.freshTemp("boolean");
            out.add(indent + t + " = !" + arg.value + ";\n");
            return new ExprResult(t, "boolean");
        }

        private ExprResult lowerBracket(BracketExpression n, MethodContext ctx, List<String> out, String indent) {
            return lowerExpression(n.f1, ctx, out, indent);
        }

        private void emitVarDecls(Map<String, String> vars, String indent, StringBuilder sb) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                sb.append(indent).append(e.getValue()).append(" ").append(e.getKey()).append(";\n");
            }
        }
    }

    private static String tokenImage(Identifier id) {
        return id.f0.tokenImage;
    }

    private static String typeToString(Type t) {
        NodeChoice ch = t.f0;
        switch (ch.which) {
            case 0:
                return "int[]";
            case 1:
                return "boolean";
            case 2:
                return "int";
            case 3:
                return tokenImage((Identifier) ch.choice);
            default:
                throw new IllegalStateException("Unknown type choice: " + ch.which);
        }
    }

    private static List<Node> nodes(NodeListOptional list) {
        List<Node> out = new ArrayList<Node>();
        for (Enumeration<Node> e = list.elements(); e.hasMoreElements();) {
            out.add(e.nextElement());
        }
        return out;
    }

    private static String join(List<String> parts, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(sep);
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
