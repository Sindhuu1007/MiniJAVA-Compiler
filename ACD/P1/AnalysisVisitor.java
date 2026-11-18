import visitor.*;
import syntaxtree.*;
import java.util.*;

class AnalysisVisitor extends GJVoidDepthFirst<Void> {
    private final ProgramInfo program;
    private final ExpressionChecker expressionChecker = new ExpressionChecker();
    private ClassInfo currentClass;

    AnalysisVisitor(ProgramInfo program) {
        this.program = program;
    }

    @Override
    public void visit(MainClass n, Void argu) {
        MethodState state = new MethodState(program, null, null);
        state.initialized.add(state.keyLocal(program.mainArgName));
        expressionChecker.visit(n.f14.f2, state);
    }

    @Override
    public void visit(syntaxtree.ClassDeclaration n, Void argu) {
        currentClass = program.getClassInfo(n.f1.f0.tokenImage);
        if (n.f4.present()) {
            for (Enumeration<Node> e = n.f4.elements(); e.hasMoreElements();) {
                analyzeMethod((MethodDeclaration) e.nextElement());
            }
        }
        currentClass = null;
    }

    @Override
    public void visit(syntaxtree.ClassExtendsDeclaration n, Void argu) {
        currentClass = program.getClassInfo(n.f1.f0.tokenImage);
        if (n.f6.present()) {
            for (Enumeration<Node> e = n.f6.elements(); e.hasMoreElements();) {
                analyzeMethod((MethodDeclaration) e.nextElement());
            }
        }
        currentClass = null;
    }

    private void analyzeMethod(MethodDeclaration n) {
        MethodInfo methodInfo = currentClass.methods.get(n.f2.f0.tokenImage);
        MethodState state = new MethodState(program, currentClass, methodInfo);

        if (n.f7.present()) {
            for (Enumeration<Node> e = n.f7.elements(); e.hasMoreElements();) {
                VarDeclaration decl = (VarDeclaration) e.nextElement();
                Node choice = decl.f0.choice;
                if (choice instanceof FinalVarDeclaration) {
                    FinalVarDeclaration fin = (FinalVarDeclaration) choice;
                    expressionChecker.visit(fin.f4, state);
                    MethodState.VarResolution res = state.resolve(fin.f2.f0.tokenImage);
                    if (res != null) {
                        state.markInitialized(res, state.initialized);
                    }
                }
            }
        }

        if (n.f8.present()) {
            for (Enumeration<Node> e = n.f8.elements(); e.hasMoreElements();) {
                Statement stmt = (Statement) e.nextElement();
                state = state.copyWith(analyzeStatement(stmt, state));
            }
        }

        expressionChecker.visit(n.f10, state);
    }

    private HashSet<String> analyzeStatement(Statement stmt, MethodState state) {
        Node choice = stmt.f0.choice;
        if (choice instanceof Block) {
            return analyzeBlock((Block) choice, state);
        }
        if (choice instanceof AssignmentStatement) {
            return handleAssignment((AssignmentStatement) choice, state);
        }
        if (choice instanceof ArrayAssignmentStatement) {
            return handleArrayAssignment((ArrayAssignmentStatement) choice, state);
        }
        if (choice instanceof syntaxtree.FieldAssignmentStatement) {
            return handleFieldAssignment((syntaxtree.FieldAssignmentStatement) choice, state);
        }
        if (choice instanceof IfStatement) {
            return handleIf((IfStatement) choice, state);
        }
        if (choice instanceof WhileStatement) {
            return handleWhile((WhileStatement) choice, state);
        }
        if (choice instanceof ForStatement) {
            return handleFor((ForStatement) choice, state);
        }
        if (choice instanceof PrintStatement) {
            expressionChecker.visit(((PrintStatement) choice).f2, state);
            return state.initialized;
        }
        return state.initialized;
    }

    private HashSet<String> analyzeBlock(Block block, MethodState state) {
        HashSet<String> current = new HashSet<>(state.initialized);
        if (block.f1.present()) {
            for (Enumeration<Node> e = block.f1.elements(); e.hasMoreElements();) {
                Statement inner = (Statement) e.nextElement();
                MethodState innerState = state.copyWith(current);
                current = analyzeStatement(inner, innerState);
            }
        }
        return current;
    }

    private HashSet<String> handleAssignment(AssignmentStatement n, MethodState state) {
        MethodState.VarResolution res = state.resolve(n.f0.f0.tokenImage);
        if (res == null) {
            throw new IssueException("Uninitialized variable found.");
        }
        if (res.info.isFinal) {
            throw new IssueException("Final variable being assigned.");
        }
        expressionChecker.visit(n.f2, state);
        HashSet<String> out = new HashSet<>(state.initialized);
        state.markInitialized(res, out);
        return out;
    }

    private HashSet<String> handleArrayAssignment(ArrayAssignmentStatement n, MethodState state) {
        state.ensureInitialized(n.f0.f0.tokenImage);
        expressionChecker.visit(n.f2, state);
        expressionChecker.visit(n.f5, state);
        return state.initialized;
    }

    private HashSet<String> handleFieldAssignment(syntaxtree.FieldAssignmentStatement n, MethodState state) {
        expressionChecker.visit(n.f0, state);
        expressionChecker.visit(n.f4, state);

        if (isThisExpression(n.f0)) {
            MethodState.VarResolution res = state.resolveFieldOnly(n.f2.f0.tokenImage);
            if (res != null) {
                if (res.info.isFinal) {
                    throw new IssueException("Final variable being assigned.");
                }
                HashSet<String> out = new HashSet<>(state.initialized);
                state.markInitialized(res, out);
                return out;
            }
        }
        return state.initialized;
    }

    private HashSet<String> handleIf(IfStatement n, MethodState state) {
        expressionChecker.visit(n.f2, state);

        MethodState thenState = state.copy();
        MethodState elseState = state.copy();

        HashSet<String> thenInit = analyzeStatement(n.f4, thenState);
        HashSet<String> elseInit = analyzeStatement(n.f6, elseState);

        HashSet<String> out = new HashSet<>();
        for (String name : thenInit) {
            if (elseInit.contains(name)) {
                out.add(name);
            }
        }
        return out;
    }

    private HashSet<String> handleWhile(WhileStatement n, MethodState state) {
        expressionChecker.visit(n.f2, state);
        MethodState bodyState = state.copy();
        analyzeStatement(n.f4, bodyState);
        return state.initialized;
    }

    private HashSet<String> handleFor(ForStatement n, MethodState state) {
        HashSet<String> base = new HashSet<>(state.initialized);

        MethodState.VarResolution initVar = state.resolve(n.f2.f0.tokenImage);
        if (initVar == null) {
            throw new IssueException("Uninitialized variable found.");
        }
        if (initVar.info.isFinal) {
            throw new IssueException("Final variable being assigned.");
        }
        MethodState initState = state.copyWith(base);
        expressionChecker.visit(n.f4, initState);
        state.markInitialized(initVar, base);

        MethodState condState = state.copyWith(base);
        expressionChecker.visit(n.f6, condState);

        MethodState bodyState = state.copyWith(base);
        HashSet<String> afterBody = analyzeStatement(n.f12, bodyState);

        MethodState.VarResolution updateVar = state.resolve(n.f8.f0.tokenImage);
        if (updateVar == null) {
            throw new IssueException("Uninitialized variable found.");
        }
        if (updateVar.info.isFinal) {
            throw new IssueException("Final variable being assigned.");
        }
        MethodState updateState = state.copyWith(afterBody);
        expressionChecker.visit(n.f10, updateState);

        return base;
    }

    private boolean isThisExpression(Expression expr) {
        if (expr.f0.choice instanceof syntaxtree.PrimaryExpression) {
            syntaxtree.PrimaryExpression pe = (syntaxtree.PrimaryExpression) expr.f0.choice;
            return pe.f0.choice instanceof ThisExpression;
        }
        return false;
    }
}
