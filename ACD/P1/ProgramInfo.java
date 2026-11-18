import visitor.*;
import syntaxtree.*;
import java.util.*;

class ProgramInfo {
    final HashMap<String, ClassInfo> classes = new HashMap<>();
    String mainClassName;
    String mainArgName;

    ClassInfo ensureClass(String name, String parent) {
        ClassInfo info = classes.get(name);
        if (info == null) {
            info = new ClassInfo(name, parent);
            classes.put(name, info);
        } else if (parent != null) {
            info.parentName = parent;
        }
        return info;
    }

    ClassInfo getClassInfo(String name) {
        return classes.get(name);
    }
}

class SymbolTable {
    final Map<String, ClassInfo> classes = new HashMap<>();
    String mainClassName;

    ClassInfo ensureClass(String name) {
        return classes.computeIfAbsent(name, k -> new ClassInfo());
    }

    static class ClassInfo {
        final Map<String, VarInfo> fields = new HashMap<>();
        final Map<String, MethodInfo> methods = new HashMap<>();
        String parentName;
    }

    static class MethodInfo {
        final Map<String, VarInfo> params = new HashMap<>();
        final Map<String, VarInfo> locals = new HashMap<>();
    }

    static class VarInfo {
        final boolean isFinal;

        VarInfo(boolean isFinal) {
            this.isFinal = isFinal;
        }
    }
}

class ClassInfo {
    final String name;
    String parentName;
    final HashMap<String, VarInfo> fields = new HashMap<>();
    final HashMap<String, MethodInfo> methods = new HashMap<>();

    ClassInfo(String name, String parentName) {
        this.name = name;
        this.parentName = parentName;
    }
}

class MethodInfo {
    final String name;
    final LinkedHashMap<String, VarInfo> parameters = new LinkedHashMap<>();
    final HashMap<String, VarInfo> locals = new HashMap<>();

    MethodInfo(String name) {
        this.name = name;
    }
}

class VarInfo {
    final boolean isFinal;
    final boolean initialized;

    VarInfo(boolean isFinal, boolean initialized) {
        this.isFinal = isFinal;
        this.initialized = initialized;
    }
}

class Scope {
    final String className;
    final String methodName;

    Scope(String className, String methodName) {
        this.className = className;
        this.methodName = methodName;
    }

    Scope withMethod(String methodName) {
        return new Scope(className, methodName);
    }
}

class ExpressionChecker extends GJVoidDepthFirst<MethodState> {
    public void visit(PrimaryExpression n, MethodState state) {
        if (n.f0.choice instanceof Identifier) {
            Identifier id = (Identifier) n.f0.choice;
            state.ensureInitialized(id.f0.tokenImage);
            return;
        }
        n.f0.choice.accept(this, state);
    }
}

class MethodState {
    enum Scope { LOCAL, FIELD }

    static final class VarResolution {
        final String name;
        final VarInfo info;
        final Scope scope;

        VarResolution(String name, VarInfo info, Scope scope) {
            this.name = name;
            this.info = info;
            this.scope = scope;
        }
    }

    final ProgramInfo program;
    final ClassInfo clazz;
    final MethodInfo method;
    final HashSet<String> initialized;

    MethodState(ProgramInfo program, ClassInfo clazz, MethodInfo method) {
        this(program, clazz, method, new HashSet<>());
        seedInitialization();
    }

    MethodState(ProgramInfo program, ClassInfo clazz, MethodInfo method, HashSet<String> initialized) {
        this.program = program;
        this.clazz = clazz;
        this.method = method;
        this.initialized = initialized;
    }

    MethodState copy() {
        return new MethodState(program, clazz, method, new HashSet<>(initialized));
    }

    MethodState copyWith(HashSet<String> init) {
        return new MethodState(program, clazz, method, new HashSet<>(init));
    }

    void seedInitialization() {
        if (method != null) {
            for (Map.Entry<String, VarInfo> entry : method.parameters.entrySet()) {
                if (entry.getValue().initialized) {
                    initialized.add(keyLocal(entry.getKey()));
                }
            }
            for (Map.Entry<String, VarInfo> entry : method.locals.entrySet()) {
                if (entry.getValue().initialized) {
                    initialized.add(keyLocal(entry.getKey()));
                }
            }
        }
        ClassInfo cursor = clazz;
        while (cursor != null) {
            for (Map.Entry<String, VarInfo> entry : cursor.fields.entrySet()) {
                if (entry.getValue().initialized) {
                    initialized.add(keyField(entry.getKey()));
                }
            }
            cursor = cursor.parentName == null ? null : program.getClassInfo(cursor.parentName);
        }
    }

    String keyLocal(String name) {
        return "L:" + name;
    }

    String keyField(String name) {
        return "F:" + name;
    }

    VarResolution resolve(String name) {
        if (method != null) {
            VarInfo local = method.locals.get(name);
            if (local != null) {
                return new VarResolution(name, local, Scope.LOCAL);
            }
            VarInfo param = method.parameters.get(name);
            if (param != null) {
                return new VarResolution(name, param, Scope.LOCAL);
            }
        }
        ClassInfo cursor = clazz;
        while (cursor != null) {
            VarInfo field = cursor.fields.get(name);
            if (field != null) {
                return new VarResolution(name, field, Scope.FIELD);
            }
            cursor = cursor.parentName == null ? null : program.getClassInfo(cursor.parentName);
        }
        return null;
    }

    VarResolution resolveFieldOnly(String name) {
        ClassInfo cursor = clazz;
        while (cursor != null) {
            VarInfo field = cursor.fields.get(name);
            if (field != null) {
                return new VarResolution(name, field, Scope.FIELD);
            }
            cursor = cursor.parentName == null ? null : program.getClassInfo(cursor.parentName);
        }
        return null;
    }

    void ensureInitialized(String name) {
        VarResolution res = resolve(name);
        if (res == null) {
            throw new IssueException("Uninitialized variable found.");
        }
        String key = res.scope == Scope.FIELD ? keyField(res.name) : keyLocal(res.name);
        if (!initialized.contains(key)) {
            throw new IssueException("Uninitialized variable found.");
        }
    }

    void markInitialized(VarResolution res, HashSet<String> target) {
        String key = res.scope == Scope.FIELD ? keyField(res.name) : keyLocal(res.name);
        target.add(key);
    }
}

