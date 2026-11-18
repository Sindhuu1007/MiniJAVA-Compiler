import visitor.*;
import syntaxtree.*;
import java.util.*;

class SymbolTableBuilder extends GJVoidDepthFirst<Void> {
    private final ProgramInfo program = new ProgramInfo();
    private ClassInfo currentClass;
    private MethodInfo currentMethod;

    ProgramInfo build(Goal goal) {
        goal.accept(this, null);
        return program;
    }

    @Override
    public void visit(MainClass n, Void argu) {
        program.mainClassName = n.f1.f0.tokenImage;
        program.mainArgName = n.f11.f0.tokenImage;
    }

    @Override
    public void visit(ClassDeclaration n, Void argu) {
        currentClass = program.ensureClass(n.f1.f0.tokenImage, null);
        if (n.f3.present()) {
            for (Enumeration<Node> e = n.f3.elements(); e.hasMoreElements();) {
                e.nextElement().accept(this, null);
            }
        }
        if (n.f4.present()) {
            for (Enumeration<Node> e = n.f4.elements(); e.hasMoreElements();) {
                e.nextElement().accept(this, null);
            }
        }
        currentClass = null;
    }

    @Override
    public void visit(ClassExtendsDeclaration n, Void argu) {
        currentClass = program.ensureClass(n.f1.f0.tokenImage, n.f3.f0.tokenImage);
        if (n.f5.present()) {
            for (Enumeration<Node> e = n.f5.elements(); e.hasMoreElements();) {
                e.nextElement().accept(this, null);
            }
        }
        if (n.f6.present()) {
            for (Enumeration<Node> e = n.f6.elements(); e.hasMoreElements();) {
                e.nextElement().accept(this, null);
            }
        }
        currentClass = null;
    }

    @Override
    public void visit(MethodDeclaration n, Void argu) {
        MethodInfo method = new MethodInfo(n.f2.f0.tokenImage);
        currentMethod = method;

        if (n.f4.present()) {
            FormalParameterList params = (FormalParameterList) n.f4.node;
            addParameter(params.f0);
            if (params.f1.present()) {
                for (Enumeration<Node> e = params.f1.elements(); e.hasMoreElements();) {
                    FormalParameterRest rest = (FormalParameterRest) e.nextElement();
                    addParameter(rest.f1);
                }
            }
        }

        if (n.f7.present()) {
            for (Enumeration<Node> e = n.f7.elements(); e.hasMoreElements();) {
                e.nextElement().accept(this, null);
            }
        }

        currentClass.methods.put(method.name, method);
        currentMethod = null;
    }

    @Override
    public void visit(syntaxtree.SimpleVarDeclaration n, Void argu) {
        addVariable(n.f1.f0.tokenImage, new VarInfo(false, false));
    }

    @Override
    public void visit(syntaxtree.FinalVarDeclaration n, Void argu) {
        addVariable(n.f2.f0.tokenImage, new VarInfo(true, true));
    }

    private void addParameter(FormalParameter param) {
        currentMethod.parameters.put(param.f1.f0.tokenImage, new VarInfo(false, true));
    }

    private void addVariable(String name, VarInfo info) {
        if (currentMethod != null) {
            currentMethod.locals.put(name, info);
        } else if (currentClass != null) {
            // Fields get default initialization in Java/BuritoJava runtime, so mark them as initialized.
            VarInfo stored = info.isFinal ? info : new VarInfo(false, true);
            currentClass.fields.put(name, stored);
        }
    }
}
