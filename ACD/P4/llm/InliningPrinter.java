import syntaxtree.*;
import visitor.DepthFirstVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class InliningPrinter extends DepthFirstVisitor {
    private final ProgramModel model;
    private final StringBuilder out = new StringBuilder();
    private int inlineCounter;

    InliningPrinter(ProgramModel model) {
        this.model = model;
    }

    String render(Goal goal) {
        renderMainClass(goal.f0);
        for (Node node : nodes(goal.f1)) {
            renderTypeDeclaration((TypeDeclaration) node);
        }
        return out.toString();
    }

    private void renderMainClass(MainClass node) {
        String className = identifier(node.f1);
        append(out, 0, "class " + className + " {");
        append(out, 1, "public static void main (String[] args) {");

        StringBuilder body = new StringBuilder();
        Frame frame = Frame.forMain(model, className, body);

        for (Node nodeDecl : nodes(node.f14)) {
            VarDeclaration decl = (VarDeclaration) nodeDecl;
            String type = ProgramModel.typeToString(decl.f0);
            frame.declareLocal(identifier(decl.f1), type);
            append(out, 2, renderVarDeclaration(decl));
        }

        processStatements(node.f15, frame, true);
        emitTemps(frame, 2, out);
        out.append(body);
        append(out, 1, "}");
        append(out, 0, "}");
    }

    private void renderTypeDeclaration(TypeDeclaration node) {
        if (node.f0.choice instanceof ClassDeclaration) {
            renderClassDeclaration((ClassDeclaration) node.f0.choice);
        } else {
            renderClassExtendsDeclaration((ClassExtendsDeclaration) node.f0.choice);
        }
    }

    private void renderClassDeclaration(ClassDeclaration node) {
        String className = identifier(node.f1);
        append(out, 0, "class " + className + "{");
        renderClassBody(className, node.f3, node.f4);
        append(out, 0, "}");
    }

    private void renderClassExtendsDeclaration(ClassExtendsDeclaration node) {
        String className = identifier(node.f1);
        String parentName = identifier(node.f3);
        append(out, 0, "class " + className + " extends " + parentName + "{");
        renderClassBody(className, node.f5, node.f6);
        append(out, 0, "}");
    }

    private void renderClassBody(String className, NodeListOptional fields, NodeListOptional methods) {
        for (Node node : nodes(fields)) {
            append(out, 1, renderVarDeclaration((VarDeclaration) node));
        }
        for (Node node : nodes(methods)) {
            renderMethod(className, (MethodDeclaration) node);
        }
    }

    private void renderMethod(String className, MethodDeclaration node) {
        ProgramModel.MethodInfo methodInfo = model.methodsByNode.get(node);
        StringBuilder body = new StringBuilder();
        Frame frame = Frame.forMethod(model, methodInfo, className, body);

        append(out, 1, renderMethodHeader(node));
        for (Node declNode : nodes(node.f7)) {
            VarDeclaration decl = (VarDeclaration) declNode;
            String type = ProgramModel.typeToString(decl.f0);
            frame.declareLocal(identifier(decl.f1), type);
            append(out, 2, renderVarDeclaration(decl));
        }

        processStatements(node.f8, frame, true);
        emitTemps(frame, 2, out);
        out.append(body);
        append(out, 2, "return " + renderConstOrId(node.f10, frame) + ";");
        append(out, 1, "}");
    }

    private String renderMethodHeader(MethodDeclaration node) {
        StringBuilder builder = new StringBuilder();
        builder.append("public ");
        builder.append(ProgramModel.typeToString(node.f1));
        builder.append(' ');
        builder.append(identifier(node.f2));
        builder.append('(');
        if (node.f4.present()) {
            FormalParameterList params = (FormalParameterList) node.f4.node;
            builder.append(renderFormalParameter(params.f0));
            for (Node rest : nodes(params.f1)) {
                builder.append(", ");
                builder.append(renderFormalParameter(((FormalParameterRest) rest).f1));
            }
        }
        builder.append(") {");
        return builder.toString();
    }

    private String renderFormalParameter(FormalParameter node) {
        return ProgramModel.typeToString(node.f0) + " " + identifier(node.f1);
    }

    private String renderVarDeclaration(VarDeclaration node) {
        return ProgramModel.typeToString(node.f0) + " " + identifier(node.f1) + ";";
    }

    private void processStatements(NodeListOptional statements, Frame frame, boolean emit) {
        for (Node node : nodes(statements)) {
            processStatement((Statement) node, frame, emit);
        }
    }

    private void processStatement(Statement statement, Frame frame, boolean emit) {
        Node choice = statement.f0.choice;
        if (choice instanceof Block) {
            processBlock((Block) choice, frame, emit);
            return;
        }
        if (choice instanceof AssignmentStatement) {
            processAssignment((AssignmentStatement) choice, frame, emit);
            return;
        }
        if (choice instanceof ArrayAssignmentStatement) {
            processArrayAssignment((ArrayAssignmentStatement) choice, frame, emit);
            return;
        }
        if (choice instanceof FieldAssignmentStatement) {
            processFieldAssignment((FieldAssignmentStatement) choice, frame, emit);
            return;
        }
        if (choice instanceof IfStatement) {
            processIf((IfStatement) choice, frame, emit);
            return;
        }
        if (choice instanceof WhileStatement) {
            processWhile((WhileStatement) choice, frame, emit);
            return;
        }
        if (choice instanceof ForStatement) {
            processFor((ForStatement) choice, frame, emit);
            return;
        }
        if (choice instanceof PrintStatement) {
            processPrint((PrintStatement) choice, frame, emit);
            return;
        }
        if (choice instanceof MessageSendStatement) {
            processMessageSendStatement((MessageSendStatement) choice, frame, emit);
        }
    }

    private void processBlock(Block block, Frame frame, boolean emit) {
        if (emit) {
            append(frame.body, frame.indent, "{");
        }
        Frame child = frame.branchFrame(frame.body);
        child.indent = frame.indent + 1;
        processStatements(block.f1, child, emit);
        frame.mergeFrom(child);
        if (emit) {
            append(frame.body, frame.indent, "}");
        }
    }

    private void processAssignment(AssignmentStatement node, Frame frame, boolean emit) {
        Value rhs = evaluateRhs(node.f2, frame, emit);
        String name = identifier(node.f0);
        // if the name is not a declared local/param and it's actually a field, treat as field write
        if (!frame.varTypes.containsKey(name) && fieldOwner(frame.currentClass, name) != null) {
            frame.writeField(frame.thisText, name, rhs.types);
            if (emit) {
                String fieldLhs = "this".equals(frame.thisText) ? name : frame.thisText + "." + name;
                if (isConstOrIdText(rhs.text)) {
                    append(frame.body, frame.indent, fieldLhs + " = " + rhs.text + ";");
                } else {
                    String owner = fieldOwner(frame.currentClass, name);
                    String fieldType = owner == null ? null : model.declaredFieldType(owner, name);
                    String temp = frame.newTemp("st_" + name, chooseTempType(rhs, fieldType));
                    append(frame.body, frame.indent, temp + " = " + rhs.text + ";");
                    append(frame.body, frame.indent, fieldLhs + " = " + temp + ";");
                }
            }
        } else {
            frame.assign(name, rhs.types);
            if (emit) {
                append(frame.body, frame.indent, renderName(name, frame) + " = " + rhs.text + ";");
            }
        }
    }

    private void processArrayAssignment(ArrayAssignmentStatement node, Frame frame, boolean emit) {
        if (emit) {
            append(frame.body, frame.indent,
                    renderName(identifier(node.f0), frame) + "[" + renderConstOrId(node.f2, frame) + "] = " + renderConstOrId(node.f5, frame) + ";");
        }
    }

    private void processFieldAssignment(FieldAssignmentStatement node, Frame frame, boolean emit) {
        Value value = evaluateConstOrId(node.f4, frame, emit);
        frame.writeField(identifier(node.f0), identifier(node.f2), value.types);
        if (emit) {
            append(frame.body, frame.indent,
                    renderName(identifier(node.f0), frame) + "." + identifier(node.f2) + " = " + value.text + ";");
        }
    }

    private void processIf(IfStatement node, Frame frame, boolean emit) {
        Frame thenFrame = frame.branchFrame(new StringBuilder());
        Frame elseFrame = frame.branchFrame(new StringBuilder());
        thenFrame.indent = frame.indent + 1;
        elseFrame.indent = frame.indent + 1;
        processStatement(node.f4, thenFrame, emit);
        processStatement(node.f6, elseFrame, emit);
        if (emit) {
            append(frame.body, frame.indent, "if (" + renderName(identifier(node.f2), frame) + ")");
            frame.body.append(thenFrame.body);
            append(frame.body, frame.indent, "else");
            frame.body.append(elseFrame.body);
        }
        frame.mergeFrom(thenFrame);
        frame.mergeFrom(elseFrame);
    }

    private void processWhile(WhileStatement node, Frame frame, boolean emit) {
        Frame bodyFrame = frame.branchFrame(new StringBuilder());
        bodyFrame.indent = frame.indent + 1;
        processStatement(node.f4, bodyFrame, emit);
        if (emit) {
            append(frame.body, frame.indent, "while (" + renderName(identifier(node.f2), frame) + ")");
            frame.body.append(bodyFrame.body);
        }
        frame.mergeFrom(bodyFrame);
    }

    private void processFor(ForStatement node, Frame frame, boolean emit) {
        Frame bodyFrame = frame.branchFrame(new StringBuilder());
        bodyFrame.indent = frame.indent + 1;
        if (emit) {
            append(frame.body, frame.indent,
                    "for (" + renderName(identifier(node.f2), frame) + " = " + renderExpression(node.f4, frame) + "; " + renderExpression(node.f6, frame) + "; " + renderName(identifier(node.f8), frame) + " = " + renderExpression(node.f10, frame) + ")");
        }
        processStatement(node.f12, bodyFrame, emit);
        if (emit) {
            frame.body.append(bodyFrame.body);
        }
        frame.mergeFrom(bodyFrame);
    }

    private void processPrint(PrintStatement node, Frame frame, boolean emit) {
        if (emit) {
            append(frame.body, frame.indent, "System.out.println (" + renderConstOrId(node.f2, frame) + ");");
        }
    }

    private void processMessageSendStatement(MessageSendStatement node, Frame frame, boolean emit) {
        boolean marked = node.f0.present();
        Node choice = node.f1.choice;
        if (choice instanceof VoidMessageSendStmt) {
            CallResult result = executeCall(((VoidMessageSendStmt) choice).f0, null, frame, emit, marked);
            if (emit && !result.inlined) {
                append(frame.body, frame.indent, result.renderedCall + ";");
            }
            return;
        }
        RetMessageSendStmt ret = (RetMessageSendStmt) choice;
            CallResult result = executeCall(ret.f2, identifier(ret.f0), frame, emit, marked);
        if (emit && result.inlined) {
                append(frame.body, frame.indent, renderName(identifier(ret.f0), frame) + " = " + result.returnValue.text + ";");
        } else if (emit) {
            append(frame.body, frame.indent, renderName(identifier(ret.f0), frame) + " = " + result.renderedCall + ";");
                frame.assign(identifier(ret.f0), result.returnValue.types);
        }
    }

    private CallResult executeCall(MessageSend send, String targetName, Frame frame, boolean emit, boolean allowInline) {
        Value receiver = evaluatePrimary(send.f0, frame, emit);
        List<Value> args = evaluateArgs(send.f4, frame, emit);
        if (receiver.types.isEmpty()) {
            String rendered = renderMessageSend(send, frame, receiver.text);
            return new CallResult(false, rendered, Value.empty(), frame);
        }

        // Grammar does not allow chained field access directly as message receiver;
        // normalize to a temporary identifier when receiver is a field/dot expression.
        if (emit && receiver.text.contains(".")) {
            String recvType = receiver.types.iterator().next();
            String recvTemp = frame.newTemp("ld_recv", recvType);
            append(frame.body, frame.indent, recvTemp + " = " + receiver.text + ";");
            receiver = new Value(receiver.types, recvTemp);
        }

        boolean canInline = allowInline && receiver.types.size() == 1;
        if (canInline) {
            String receiverType = receiver.types.iterator().next();
            ProgramModel.MethodInfo target = model.resolveMethod(receiverType, identifier(send.f2));
            if (target != null && !model.canReach(target, frame.method)) {
                String inlinePrefix = emit ? nextInlinePrefix() : null;
                boolean needsReceiverTemp = methodNeedsReceiverTemp(target);
                Frame child = emit ? frame.inlineChild(target, receiver, args, inlinePrefix, needsReceiverTemp) : frame.analysisChild(target, receiver, args);
                processMethodBody(target, child, emit);
                frame.mergeFieldsFrom(child);
                frame.tempDecls.addAll(child.tempDecls);
                return new CallResult(true, null, child.returnValue, child);
            }
        }

        LinkedHashSet<String> returnTypes = new LinkedHashSet<>();
        Frame joined = null;
        for (String type : receiver.types) {
            ProgramModel.MethodInfo target = model.resolveMethod(type, identifier(send.f2));
            if (target == null) {
                continue;
            }
            if (model.canReach(target, frame.method)) {
                returnTypes.addAll(fullReturnType(target.returnType));
                continue;
            }
            Frame child = frame.analysisChild(target, receiver, args);
            processMethodBody(target, child, false);
            returnTypes.addAll(child.returnValue.types);
            joined = joined == null ? child : joined.joinFields(child);
        }
        if (joined != null) {
            frame.mergeFieldsFrom(joined);
        }
        String rendered = emit ? renderMessageSend(send, frame, receiver.text) : null;
        return new CallResult(false, rendered, new Value(returnTypes, rendered == null ? "" : rendered), joined == null ? frame : joined);
    }

    private String nextInlinePrefix() {
        return "inline" + (++inlineCounter) + "_";
    }

    private boolean methodNeedsReceiverTemp(ProgramModel.MethodInfo method) {
        LinkedHashSet<String> declared = new LinkedHashSet<>();
        for (ProgramModel.VarInfo parameter : method.parameters) {
            declared.add(parameter.name);
        }
        for (ProgramModel.VarInfo local : method.locals) {
            declared.add(local.name);
        }
        return listNeedsReceiver(method.node.f8, declared);
    }

    private boolean listNeedsReceiver(NodeListOptional list, Set<String> declared) {
        for (Node node : nodes(list)) {
            if (statementNeedsReceiver((Statement) node, declared)) {
                return true;
            }
        }
        return false;
    }

    private boolean statementNeedsReceiver(Statement statement, Set<String> declared) {
        Node choice = statement.f0.choice;
        if (choice instanceof Block) {
            return listNeedsReceiver(((Block) choice).f1, declared);
        }
        if (choice instanceof AssignmentStatement) {
            AssignmentStatement s = (AssignmentStatement) choice;
            String lhs = identifier(s.f0);
            return identifierNeedsReceiver(lhs, declared) || rhsNeedsReceiver(s.f2, declared);
        }
        if (choice instanceof ArrayAssignmentStatement) {
            ArrayAssignmentStatement s = (ArrayAssignmentStatement) choice;
            return (constOrIdNeedsReceiver(s.f2, declared) || constOrIdNeedsReceiver(s.f5, declared));
        }
        if (choice instanceof FieldAssignmentStatement) {
            FieldAssignmentStatement s = (FieldAssignmentStatement) choice;
            return identifierNeedsReceiver(identifier(s.f0), declared) || constOrIdNeedsReceiver(s.f4, declared);
        }
        if (choice instanceof IfStatement) {
            IfStatement s = (IfStatement) choice;
            return identifierNeedsReceiver(identifier(s.f2), declared)
                    || statementNeedsReceiver(s.f4, declared)
                    || statementNeedsReceiver(s.f6, declared);
        }
        if (choice instanceof WhileStatement) {
            WhileStatement s = (WhileStatement) choice;
            return identifierNeedsReceiver(identifier(s.f2), declared) || statementNeedsReceiver(s.f4, declared);
        }
        if (choice instanceof ForStatement) {
            ForStatement s = (ForStatement) choice;
            return identifierNeedsReceiver(identifier(s.f2), declared)
                    || expressionNeedsReceiver(s.f4, declared)
                    || expressionNeedsReceiver(s.f6, declared)
                    || identifierNeedsReceiver(identifier(s.f8), declared)
                    || expressionNeedsReceiver(s.f10, declared)
                    || statementNeedsReceiver(s.f12, declared);
        }
        if (choice instanceof PrintStatement) {
            return constOrIdNeedsReceiver(((PrintStatement) choice).f2, declared);
        }
        if (choice instanceof MessageSendStatement) {
            return messageSendStatementNeedsReceiver((MessageSendStatement) choice, declared);
        }
        return false;
    }

    private boolean messageSendStatementNeedsReceiver(MessageSendStatement s, Set<String> declared) {
        Node inner = s.f1.choice;
        if (inner instanceof VoidMessageSendStmt) {
            return messageSendNeedsReceiver(((VoidMessageSendStmt) inner).f0, declared);
        }
        RetMessageSendStmt ret = (RetMessageSendStmt) inner;
        return identifierNeedsReceiver(identifier(ret.f0), declared) || messageSendNeedsReceiver(ret.f2, declared);
    }

    private boolean rhsNeedsReceiver(RhsExpression rhs, Set<String> declared) {
        Node choice = rhs.f0.choice;
        if (choice instanceof DotExpression) {
            return identifierNeedsReceiver(identifier(((DotExpression) choice).f0), declared);
        }
        return expressionNeedsReceiver((Expression) choice, declared);
    }

    private boolean expressionNeedsReceiver(Expression expression, Set<String> declared) {
        Node choice = expression.f0.choice;
        if (choice instanceof PrimaryExpression) {
            return primaryNeedsReceiver((PrimaryExpression) choice, declared);
        }
        if (choice instanceof AndExpression) {
            AndExpression e = (AndExpression) choice;
            return constOrIdNeedsReceiver(e.f0, declared) || constOrIdNeedsReceiver(e.f2, declared);
        }
        if (choice instanceof CompareExpression) {
            CompareExpression e = (CompareExpression) choice;
            return constOrIdNeedsReceiver(e.f0, declared) || constOrIdNeedsReceiver(e.f2, declared);
        }
        if (choice instanceof PlusExpression) {
            PlusExpression e = (PlusExpression) choice;
            return constOrIdNeedsReceiver(e.f0, declared) || constOrIdNeedsReceiver(e.f2, declared);
        }
        if (choice instanceof MinusExpression) {
            MinusExpression e = (MinusExpression) choice;
            return constOrIdNeedsReceiver(e.f0, declared) || constOrIdNeedsReceiver(e.f2, declared);
        }
        if (choice instanceof TimesExpression) {
            TimesExpression e = (TimesExpression) choice;
            return constOrIdNeedsReceiver(e.f0, declared) || constOrIdNeedsReceiver(e.f2, declared);
        }
        if (choice instanceof ArrayLookup) {
            ArrayLookup e = (ArrayLookup) choice;
            return identifierNeedsReceiver(identifier(e.f0), declared) || constOrIdNeedsReceiver(e.f2, declared);
        }
        if (choice instanceof ArrayLength) {
            return identifierNeedsReceiver(identifier(((ArrayLength) choice).f0), declared);
        }
        return false;
    }

    private boolean primaryNeedsReceiver(PrimaryExpression primary, Set<String> declared) {
        Node choice = primary.f0.choice;
        if (choice instanceof ThisExpression) {
            return true;
        }
        if (choice instanceof Identifier) {
            return identifierNeedsReceiver(identifier((Identifier) choice), declared);
        }
        if (choice instanceof NotExpression) {
            return identifierNeedsReceiver(identifier(((NotExpression) choice).f1), declared);
        }
        return false;
    }

    private boolean constOrIdNeedsReceiver(ConstOrId value, Set<String> declared) {
        Node choice = value.f0.choice;
        if (choice instanceof Identifier) {
            return identifierNeedsReceiver(identifier((Identifier) choice), declared);
        }
        return false;
    }

    private boolean messageSendNeedsReceiver(MessageSend send, Set<String> declared) {
        if (primaryNeedsReceiver(send.f0, declared)) {
            return true;
        }
        if (send.f4.present()) {
            ArgList args = (ArgList) send.f4.node;
            if (constOrIdNeedsReceiver(args.f0, declared)) {
                return true;
            }
            for (Node node : nodes(args.f1)) {
                if (constOrIdNeedsReceiver(((ArgRest) node).f1, declared)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean identifierNeedsReceiver(String name, Set<String> declared) {
        return !declared.contains(name);
    }

    private void processMethodBody(ProgramModel.MethodInfo method, Frame frame, boolean emit) {
        frame.method = method;
        frame.currentClass = method.ownerClass;
        processStatements(method.node.f8, frame, emit);
        frame.returnValue = evaluateConstOrId(method.node.f10, frame, emit);
    }

    private List<Value> evaluateArgs(NodeOptional args, Frame frame, boolean emit) {
        if (!args.present()) {
            return Collections.emptyList();
        }
        ArgList list = (ArgList) args.node;
        List<Value> values = new ArrayList<>();
        values.add(evaluateConstOrId(list.f0, frame, emit));
        for (Node node : nodes(list.f1)) {
            values.add(evaluateConstOrId(((ArgRest) node).f1, frame, emit));
        }
        return values;
    }

    private Value evaluateRhs(RhsExpression rhs, Frame frame, boolean emit) {
        Node choice = rhs.f0.choice;
        if (choice instanceof DotExpression) {
            DotExpression dot = (DotExpression) choice;
            return readField(identifier(dot.f0), identifier(dot.f2), frame);
        }
        return evaluateExpression((Expression) choice, frame, emit);
    }

    private Value evaluateExpression(Expression expression, Frame frame, boolean emit) {
        Node choice = expression.f0.choice;
        if (choice instanceof PrimaryExpression) {
            return evaluatePrimary((PrimaryExpression) choice, frame, emit);
        }
        if (choice instanceof AndExpression) {
            AndExpression node = (AndExpression) choice;
            return new Value(Collections.emptySet(), renderConstOrId(node.f0, frame) + " & " + renderConstOrId(node.f2, frame));
        }
        if (choice instanceof CompareExpression) {
            CompareExpression node = (CompareExpression) choice;
            return new Value(Collections.emptySet(), renderConstOrId(node.f0, frame) + " < " + renderConstOrId(node.f2, frame));
        }
        if (choice instanceof PlusExpression) {
            PlusExpression node = (PlusExpression) choice;
            return new Value(Collections.emptySet(), renderConstOrId(node.f0, frame) + " + " + renderConstOrId(node.f2, frame));
        }
        if (choice instanceof MinusExpression) {
            MinusExpression node = (MinusExpression) choice;
            return new Value(Collections.emptySet(), renderConstOrId(node.f0, frame) + " - " + renderConstOrId(node.f2, frame));
        }
        if (choice instanceof TimesExpression) {
            TimesExpression node = (TimesExpression) choice;
            return new Value(Collections.emptySet(), renderConstOrId(node.f0, frame) + " * " + renderConstOrId(node.f2, frame));
        }
        if (choice instanceof ArrayLookup) {
            ArrayLookup node = (ArrayLookup) choice;
                return new Value(Collections.emptySet(), renderName(identifier(node.f0), frame) + "[" + renderConstOrId(node.f2, frame) + "]");
        }
        if (choice instanceof ArrayLength) {
            ArrayLength node = (ArrayLength) choice;
                return new Value(Collections.emptySet(), renderName(identifier(node.f0), frame) + ".length");
        }
        return Value.empty();
    }

    private Value evaluatePrimary(PrimaryExpression expression, Frame frame, boolean emit) {
        Node choice = expression.f0.choice;
        if (choice instanceof IntegerLiteral) {
            return new Value(Collections.emptySet(), renderIntegerLiteral((IntegerLiteral) choice));
        }
        if (choice instanceof TrueLiteral) {
            return new Value(Collections.emptySet(), "true");
        }
        if (choice instanceof FalseLiteral) {
            return new Value(Collections.emptySet(), "false");
        }
        if (choice instanceof Identifier) {
                String name = identifier((Identifier) choice);
            // if not a local and is a field, render as this.field
            if (!frame.varTypes.containsKey(name) && fieldOwner(frame.currentClass, name) != null) {
                Value field = readField(frame.thisText, name, frame);
                if ("this".equals(frame.thisText)) {
                    return new Value(field.types, name);
                }
                return field;
            }
            return new Value(lookup(name, frame), renderName(name, frame));
        }
        if (choice instanceof ThisExpression) {
            return new Value(frame.thisValue.types, frame.thisText);
        }
        if (choice instanceof ArrayAllocationExpression) {
            ArrayAllocationExpression node = (ArrayAllocationExpression) choice;
            return new Value(Collections.emptySet(), "new int[" + renderConstOrId(node.f3, frame) + "]");
        }
        if (choice instanceof AllocationExpression) {
            AllocationExpression node = (AllocationExpression) choice;
                return new Value(Collections.singleton(identifier(node.f1)), "new " + identifier(node.f1) + "()");
        }
        if (choice instanceof NotExpression) {
            NotExpression node = (NotExpression) choice;
                return new Value(Collections.emptySet(), "!" + renderName(identifier(node.f1), frame));
        }
        return Value.empty();
    }

    private Value evaluateConstOrId(ConstOrId node, Frame frame, boolean emit) {
        Node choice = node.f0.choice;
        if (choice instanceof Identifier) {
                String name = identifier((Identifier) choice);
            if (!frame.varTypes.containsKey(name) && fieldOwner(frame.currentClass, name) != null) {
                Value field = readField(frame.thisText, name, frame);
                if ("this".equals(frame.thisText)) {
                    return new Value(field.types, name);
                }
                return field;
            }
            return new Value(lookup(name, frame), renderName(name, frame));
        }
        if (choice instanceof IntegerLiteral) {
            return new Value(Collections.emptySet(), renderIntegerLiteral((IntegerLiteral) choice));
        }
        if (choice instanceof TrueLiteral) {
            return new Value(Collections.emptySet(), "true");
        }
        if (choice instanceof FalseLiteral) {
            return new Value(Collections.emptySet(), "false");
        }
        return Value.empty();
    }

    private Value readField(String receiverName, String fieldName, Frame frame) {
        LinkedHashSet<String> types = new LinkedHashSet<>();
        for (String receiverType : lookup(receiverName, frame)) {
            String owner = fieldOwner(receiverType, fieldName);
            if (owner == null) {
                continue;
            }
            String type = model.declaredFieldType(owner, fieldName);
            if (type != null) {
                types.addAll(fullReturnType(type));
            }
        }
        return new Value(types, renderName(receiverName, frame) + "." + fieldName);
    }

    private void writeField(String receiverName, String fieldName, Set<String> value, Frame frame) {
        for (String receiverType : lookup(receiverName, frame)) {
            String owner = fieldOwner(receiverType, fieldName);
            if (owner != null) {
                frame.fieldValues.computeIfAbsent(owner + "." + fieldName, key -> new LinkedHashSet<>()).addAll(value);
            }
        }
    }

    private String fieldOwner(String className, String fieldName) {
        String current = className;
        while (current != null) {
            ProgramModel.ClassInfo info = model.getClassInfo(current);
            if (info == null) {
                return null;
            }
            if (info.fields.containsKey(fieldName)) {
                return current;
            }
            current = info.parentName;
        }
        return null;
    }

    private Set<String> lookup(String name, Frame frame) {
        if ("this".equals(name)) {
            if (!frame.thisValue.types.isEmpty()) {
                return new LinkedHashSet<>(frame.thisValue.types);
            }
            return fullReturnType(frame.currentClass);
        }
        Set<String> values = frame.varValues.get(name);
        if (values != null) {
            return values;
        }
        String type = frame.varTypes.get(name);
        if (type == null || !ProgramModel.isObjectType(type)) {
            return Collections.emptySet();
        }
        return fullReturnType(type);
    }

    private Set<String> fullReturnType(String type) {
        if (!ProgramModel.isObjectType(type)) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(model.allConcreteTypes(type));
    }

    private String renderConstOrId(ConstOrId node, Frame frame) {
        return evaluateConstOrId(node, frame, true).text;
    }

    private String renderExpression(Expression expression, Frame frame) {
        return evaluateExpression(expression, frame, true).text;
    }

    private String renderPrimary(PrimaryExpression expression, Frame frame) {
        return evaluatePrimary(expression, frame, true).text;
    }

    private String renderMessageSend(MessageSend send, Frame frame) {
        return renderMessageSend(send, frame, renderPrimary(send.f0, frame));
    }

    private String renderMessageSend(MessageSend send, Frame frame, String receiverText) {
        StringBuilder builder = new StringBuilder();
        builder.append(receiverText);
        builder.append('.');
            builder.append(identifier(send.f2));
        builder.append('(');
        if (send.f4.present()) {
            ArgList list = (ArgList) send.f4.node;
            builder.append(renderConstOrId(list.f0, frame));
            for (Node node : nodes(list.f1)) {
                builder.append(", ");
                builder.append(renderConstOrId(((ArgRest) node).f1, frame));
            }
        }
        builder.append(')');
        return builder.toString();
    }

    private String renderName(String name, Frame frame) {
        String renamed = frame.rename.get(name);
        return renamed == null ? name : renamed;
    }

    private boolean isConstOrIdText(String text) {
        return text.equals("true")
                || text.equals("false")
                || text.matches("[+-]?[0-9]+")
                || text.matches("[A-Za-z_][A-Za-z0-9_]*");
    }

    private String chooseTempType(Value value, String fallback) {
        if (value != null && !value.types.isEmpty()) {
            return value.types.iterator().next();
        }
        return fallback == null ? "int" : fallback;
    }

    private static String identifier(Identifier node) {
        return node.f0.toString();
    }

    private String renderIntegerLiteral(IntegerLiteral node) {
        Node choice = node.f0.choice;
        if (choice instanceof IntegerLiteralWithPosSign) {
            return "+" + ((IntegerLiteralWithPosSign) choice).f1;
        }
        if (choice instanceof IntegerLiteralWithNegSign) {
            return "-" + ((IntegerLiteralWithNegSign) choice).f1;
        }
        return ((PlainIntegerLiteral) choice).f0.toString();
    }

    private void emitTemps(Frame frame, int indent, StringBuilder target) {
        if (frame.tempDecls.isEmpty()) {
            return;
        }
        for (TempDecl decl : frame.tempDecls) {
            append(target, indent, decl.type + " " + decl.name + ";");
        }
        append(target, indent, "");
    }

    private void append(StringBuilder builder, int indent, String line) {
        builder.append("    ".repeat(Math.max(0, indent)));
        builder.append(line);
        builder.append('\n');
    }

    private List<Node> nodes(NodeListOptional list) {
        List<Node> result = new ArrayList<>();
        for (java.util.Enumeration<Node> e = list.elements(); e.hasMoreElements(); ) {
            result.add(e.nextElement());
        }
        return result;
    }

    private void mergeStates(Frame target, Frame source) {
        for (Map.Entry<String, Set<String>> entry : source.varValues.entrySet()) {
            target.varValues.computeIfAbsent(entry.getKey(), key -> new LinkedHashSet<>()).addAll(entry.getValue());
        }
        for (Map.Entry<String, Set<String>> entry : source.fieldValues.entrySet()) {
            target.fieldValues.computeIfAbsent(entry.getKey(), key -> new LinkedHashSet<>()).addAll(entry.getValue());
        }
    }

    private static final class Frame {
        final ProgramModel model;
        final Map<String, String> varTypes = new LinkedHashMap<>();
        final Map<String, Set<String>> varValues = new LinkedHashMap<>();
        final Map<String, Set<String>> fieldValues = new LinkedHashMap<>();
        final Map<String, String> rename = new LinkedHashMap<>();
        final LinkedHashSet<TempDecl> tempDecls = new LinkedHashSet<>();
        final TempFactory tempFactory = new TempFactory();
        String tempPrefix;
        StringBuilder body;
        ProgramModel.MethodInfo method;
        String currentClass;
        Value thisValue = Value.empty();
        String thisText = "this";
        Value returnValue = Value.empty();
        int indent = 2;

        private Frame(ProgramModel model, StringBuilder body) {
            this.model = model;
            this.body = body;
        }

        static Frame forMain(ProgramModel model, String className, StringBuilder body) {
            Frame frame = new Frame(model, body);
            frame.currentClass = className;
            return frame;
        }

        static Frame forMethod(ProgramModel model, ProgramModel.MethodInfo method, String className, StringBuilder body) {
            Frame frame = new Frame(model, body);
            frame.method = method;
            frame.currentClass = className;
            frame.thisValue = Value.singleton(className);
            frame.thisText = "this";
            for (ProgramModel.VarInfo parameter : method.parameters) {
                frame.varTypes.put(parameter.name, parameter.type);
            }
            for (ProgramModel.VarInfo local : method.locals) {
                frame.varTypes.put(local.name, local.type);
            }
            return frame;
        }

        Frame branchFrame(StringBuilder newBody) {
            Frame copy = new Frame(model, newBody);
            copy.varTypes.putAll(varTypes);
            for (Map.Entry<String, Set<String>> entry : varValues.entrySet()) {
                copy.varValues.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
            }
            for (Map.Entry<String, Set<String>> entry : fieldValues.entrySet()) {
                copy.fieldValues.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
            }
            copy.rename.putAll(rename);
            copy.tempDecls.addAll(tempDecls);
            copy.tempFactory.counter = tempFactory.counter;
            copy.method = method;
            copy.currentClass = currentClass;
            copy.tempPrefix = tempPrefix;
            copy.thisValue = thisValue;
            copy.thisText = thisText;
            copy.returnValue = returnValue;
            copy.indent = indent;
            return copy;
        }

        Frame analysisChild(ProgramModel.MethodInfo method, Value receiver, List<Value> args) {
            Frame child = branchFrame(new StringBuilder());
            child.method = method;
            child.currentClass = method.ownerClass;
            child.thisValue = receiver;
            child.thisText = receiver.text;
            child.rename.clear();
            child.indent = 2;
            child.bindArguments(method, args, false);
            return child;
        }

        Frame inlineChild(ProgramModel.MethodInfo method, Value receiver, List<Value> args, String prefix, boolean needsReceiverTemp) {
            Frame child = branchFrame(body);
            child.method = method;
            child.currentClass = method.ownerClass;
            child.tempPrefix = prefix;
            child.rename.clear();
            child.indent = indent;
            if (needsReceiverTemp) {
                String receiverType = receiver.types.isEmpty() ? currentClass : receiver.types.iterator().next();
                String temp = child.newTemp("self", receiverType == null ? currentClass : receiverType);
                child.thisValue = receiver;
                child.thisText = temp;
                child.varTypes.put(temp, receiverType == null ? currentClass : receiverType);
                child.varValues.put(temp, new LinkedHashSet<>(receiver.types));
                body.append("    ".repeat(Math.max(0, indent)));
                body.append(temp).append(" = ").append(receiver.text).append(";\n");
            } else {
                child.thisValue = receiver;
                child.thisText = receiver.text;
            }
            child.bindArguments(method, args, true);
            return child;
        }

        void bindArguments(ProgramModel.MethodInfo method, List<Value> args, boolean emitTemps) {
            int index = 0;
            for (ProgramModel.VarInfo parameter : method.parameters) {
                Value actual = index < args.size() ? args.get(index) : Value.empty();
                if (emitTemps) {
                    String temp = newTemp(parameter.name, parameter.type);
                    rename.put(parameter.name, temp);
                    varTypes.put(temp, parameter.type);
                    body.append("    ".repeat(Math.max(0, indent)));
                    body.append(temp).append(" = ").append(actual.text).append(";\n");
                }
                varTypes.put(parameter.name, parameter.type);
                varValues.put(parameter.name, new LinkedHashSet<>(actual.types));
                index++;
            }
            for (ProgramModel.VarInfo local : method.locals) {
                if (emitTemps) {
                    String temp = newTemp(local.name, local.type);
                    rename.put(local.name, temp);
                    varTypes.put(temp, local.type);
                }
                varTypes.put(local.name, local.type);
                varValues.put(local.name, new LinkedHashSet<>());
            }
        }

        String newTemp(String baseName, String type) {
            String name = tempPrefix == null ? tempFactory.next() : tempPrefix + baseName;
            tempDecls.add(new TempDecl(type, name));
            return name;
        }

        void declareLocal(String name, String type) {
            varTypes.put(name, type);
        }

        void assign(String name, Set<String> values) {
            varValues.put(name, new LinkedHashSet<>(values));
        }

        void writeField(String receiverName, String fieldName, Set<String> values) {
            for (String receiverType : lookup(receiverName)) {
                String owner = fieldOwner(receiverType, fieldName);
                if (owner != null) {
                    fieldValues.computeIfAbsent(owner + "." + fieldName, key -> new LinkedHashSet<>()).addAll(values);
                }
            }
        }

        String fieldOwner(String className, String fieldName) {
            String current = className;
            while (current != null) {
                ProgramModel.ClassInfo info = model.getClassInfo(current);
                if (info == null) {
                    return null;
                }
                if (info.fields.containsKey(fieldName)) {
                    return current;
                }
                current = info.parentName;
            }
            return null;
        }

        Set<String> lookup(String name) {
            if ("this".equals(name)) {
                if (!thisValue.types.isEmpty()) {
                    return new LinkedHashSet<>(thisValue.types);
                }
                return new LinkedHashSet<>(model.allConcreteTypes(currentClass));
            }
            Set<String> values = varValues.get(name);
            if (values != null) {
                return values;
            }
            String type = varTypes.get(name);
            if (type == null || !ProgramModel.isObjectType(type)) {
                return Collections.emptySet();
            }
            return new LinkedHashSet<>(model.allConcreteTypes(type));
        }

        Frame join(Frame other) {
            Frame joined = branchFrame(body);
            joined.mergeFrom(other);
            return joined;
        }

        void mergeFrom(Frame other) {
            for (Map.Entry<String, Set<String>> entry : other.varValues.entrySet()) {
                varValues.computeIfAbsent(entry.getKey(), key -> new LinkedHashSet<>()).addAll(entry.getValue());
            }
            for (Map.Entry<String, Set<String>> entry : other.fieldValues.entrySet()) {
                fieldValues.computeIfAbsent(entry.getKey(), key -> new LinkedHashSet<>()).addAll(entry.getValue());
            }
            tempDecls.addAll(other.tempDecls);
            rename.putAll(other.rename);
            tempFactory.counter = Math.max(tempFactory.counter, other.tempFactory.counter);
        }

        void mergeFieldsFrom(Frame other) {
            for (Map.Entry<String, Set<String>> entry : other.fieldValues.entrySet()) {
                fieldValues.computeIfAbsent(entry.getKey(), key -> new LinkedHashSet<>()).addAll(entry.getValue());
            }
        }

        Frame joinFields(Frame other) {
            Frame joined = branchFrame(body);
            for (Map.Entry<String, Set<String>> entry : other.fieldValues.entrySet()) {
                joined.fieldValues.computeIfAbsent(entry.getKey(), key -> new LinkedHashSet<>()).addAll(entry.getValue());
            }
            return joined;
        }
    }

    private static final class TempDecl {
        final String type;
        final String name;

        TempDecl(String type, String name) {
            this.type = type;
            this.name = name;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TempDecl)) {
                return false;
            }
            return name.equals(((TempDecl) other).name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    private static final class TempFactory {
        int counter;

        String next() {
            return String.format("temp_%02d", counter++);
        }
    }

    private static final class Value {
        final Set<String> types;
        final String text;

        Value(Set<String> types, String text) {
            this.types = types == null ? Collections.emptySet() : new LinkedHashSet<>(types);
            this.text = text == null ? "" : text;
        }

        static Value empty() {
            return new Value(Collections.emptySet(), "");
        }

        static Value singleton(String type) {
            return new Value(Collections.singleton(type), "this");
        }
    }

    private static final class CallResult {
        final boolean inlined;
        final String renderedCall;
        final Value returnValue;
        final Frame frame;

        CallResult(boolean inlined, String renderedCall, Value returnValue, Frame frame) {
            this.inlined = inlined;
            this.renderedCall = renderedCall;
            this.returnValue = returnValue;
            this.frame = frame;
        }
    }
}
