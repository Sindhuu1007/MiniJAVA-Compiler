import syntaxtree.*;
import visitor.DepthFirstVisitor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ProgramModel {
    final Map<String, ClassInfo> classes = new LinkedHashMap<>();
    final Map<MethodDeclaration, MethodInfo> methodsByNode = new IdentityHashMap<>();
    final Map<String, Set<String>> descendantsByType = new LinkedHashMap<>();
    final Set<MethodInfo> recursiveMethods = new LinkedHashSet<>();
    final Map<MethodInfo, Set<MethodInfo>> reachableMethods = new LinkedHashMap<>();

    ClassInfo getClassInfo(String name) {
        return classes.get(name);
    }

    MethodInfo resolveMethod(String className, String methodName) {
        String current = className;
        while (current != null) {
            ClassInfo info = classes.get(current);
            if (info == null) {
                return null;
            }
            MethodInfo method = info.methods.get(methodName);
            if (method != null) {
                return method;
            }
            current = info.parentName;
        }
        return null;
    }

    Set<String> allConcreteTypes(String staticType) {
        Set<String> result = descendantsByType.get(staticType);
        return result == null ? Collections.emptySet() : result;
    }

    String declaredFieldType(String className, String fieldName) {
        String current = className;
        while (current != null) {
            ClassInfo info = classes.get(current);
            if (info == null) {
                return null;
            }
            String type = info.fields.get(fieldName);
            if (type != null) {
                return type;
            }
            current = info.parentName;
        }
        return null;
    }

    String declaredVarType(MethodInfo method, String name) {
        if (method == null) {
            return null;
        }
        String type = method.localTypes.get(name);
        if (type != null) {
            return type;
        }
        type = method.paramTypes.get(name);
        if (type != null) {
            return type;
        }
        return declaredFieldType(method.ownerClass, name);
    }

    Collection<MethodInfo> allMethods() {
        List<MethodInfo> result = new ArrayList<>();
        for (ClassInfo info : classes.values()) {
            result.addAll(info.methods.values());
        }
        return result;
    }

    void finalizeModel() {
        Map<String, Set<String>> directChildren = new LinkedHashMap<>();
        for (ClassInfo info : classes.values()) {
            if (info.parentName != null) {
                directChildren.computeIfAbsent(info.parentName, key -> new LinkedHashSet<>()).add(info.name);
            }
        }
        for (String name : classes.keySet()) {
            LinkedHashSet<String> closure = new LinkedHashSet<>();
            Deque<String> work = new ArrayDeque<>();
            work.add(name);
            while (!work.isEmpty()) {
                String current = work.removeFirst();
                if (!closure.add(current)) {
                    continue;
                }
                Set<String> kids = directChildren.get(current);
                if (kids != null) {
                    work.addAll(kids);
                }
            }
            descendantsByType.put(name, closure);
        }
        computeRecursion();
    }

    private void computeRecursion() {
        Map<MethodInfo, Set<MethodInfo>> graph = new LinkedHashMap<>();
        for (MethodInfo method : allMethods()) {
            graph.put(method, new LinkedHashSet<>());
        }
        for (MethodInfo method : allMethods()) {
            CallGraphVisitor visitor = new CallGraphVisitor(this, method, graph.get(method));
            method.node.accept(visitor);
        }

        Set<MethodInfo> visited = new LinkedHashSet<>();
        Set<MethodInfo> stack = new LinkedHashSet<>();
        for (MethodInfo method : allMethods()) {
            if (!visited.contains(method)) {
                dfs(method, graph, visited, stack);
            }
        }

        for (MethodInfo method : allMethods()) {
            LinkedHashSet<MethodInfo> reachable = new LinkedHashSet<>();
            collectReachable(method, graph, reachable);
            reachableMethods.put(method, reachable);
        }
    }

    private void dfs(MethodInfo method,
                     Map<MethodInfo, Set<MethodInfo>> graph,
                     Set<MethodInfo> visited,
                     Set<MethodInfo> stack) {
        visited.add(method);
        stack.add(method);
        for (MethodInfo next : graph.getOrDefault(method, Collections.emptySet())) {
            if (!visited.contains(next)) {
                dfs(next, graph, visited, stack);
            } else if (stack.contains(next)) {
                recursiveMethods.add(next);
                recursiveMethods.add(method);
            }
        }
        stack.remove(method);
    }

    private void collectReachable(MethodInfo method,
                                  Map<MethodInfo, Set<MethodInfo>> graph,
                                  Set<MethodInfo> reachable) {
        if (!reachable.add(method)) {
            return;
        }
        for (MethodInfo next : graph.getOrDefault(method, Collections.emptySet())) {
            collectReachable(next, graph, reachable);
        }
    }

    boolean canReach(MethodInfo from, MethodInfo to) {
        Set<MethodInfo> reachable = reachableMethods.get(from);
        return reachable != null && reachable.contains(to);
    }

    static final class ClassInfo {
        final String name;
        final String parentName;
        final LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        final LinkedHashMap<String, MethodInfo> methods = new LinkedHashMap<>();

        ClassInfo(String name, String parentName) {
            this.name = name;
            this.parentName = parentName;
        }
    }

    static final class VarInfo {
        final String name;
        final String type;

        VarInfo(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }

    static final class MethodInfo {
        final String ownerClass;
        final String name;
        final String returnType;
        final MethodDeclaration node;
        final List<VarInfo> parameters = new ArrayList<>();
        final List<VarInfo> locals = new ArrayList<>();
        final LinkedHashMap<String, String> paramTypes = new LinkedHashMap<>();
        final LinkedHashMap<String, String> localTypes = new LinkedHashMap<>();

        MethodInfo(String ownerClass, String name, String returnType, MethodDeclaration node) {
            this.ownerClass = ownerClass;
            this.name = name;
            this.returnType = returnType;
            this.node = node;
        }

        boolean isRecursive(ProgramModel model) {
            return model.recursiveMethods.contains(this);
        }

        String signature() {
            StringBuilder builder = new StringBuilder();
            builder.append(ownerClass).append('.').append(name).append('(');
            boolean first = true;
            for (VarInfo var : parameters) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(var.type);
            }
            builder.append(')');
            return builder.toString();
        }
    }

    static final class Builder extends DepthFirstVisitor {
        private final ProgramModel model;
        private ClassInfo currentClass;
        private MethodInfo currentMethod;

        Builder(ProgramModel model) {
            this.model = model;
        }

        @Override
        public void visit(MainClass n) {
            String className = identifier(n.f1);
            currentClass = new ClassInfo(className, null);
            model.classes.put(className, currentClass);
            super.visit(n);
            currentClass = null;
        }

        @Override
        public void visit(ClassDeclaration n) {
            String className = identifier(n.f1);
            currentClass = new ClassInfo(className, null);
            model.classes.put(className, currentClass);
            super.visit(n);
            currentClass = null;
        }

        @Override
        public void visit(ClassExtendsDeclaration n) {
            String className = identifier(n.f1);
            String parentName = identifier(n.f3);
            currentClass = new ClassInfo(className, parentName);
            model.classes.put(className, currentClass);
            super.visit(n);
            currentClass = null;
        }

        @Override
        public void visit(MethodDeclaration n) {
            String methodName = identifier(n.f2);
            String returnType = typeToString(n.f1);
            currentMethod = new MethodInfo(currentClass.name, methodName, returnType, n);
            currentClass.methods.put(methodName, currentMethod);
            model.methodsByNode.put(n, currentMethod);
            super.visit(n);
            currentMethod = null;
        }

        @Override
        public void visit(FormalParameter n) {
            if (currentMethod != null) {
                String name = identifier(n.f1);
                String type = typeToString(n.f0);
                currentMethod.parameters.add(new VarInfo(name, type));
                currentMethod.paramTypes.put(name, type);
            }
            super.visit(n);
        }

        @Override
        public void visit(VarDeclaration n) {
            String name = identifier(n.f1);
            String type = typeToString(n.f0);
            if (currentMethod != null) {
                currentMethod.locals.add(new VarInfo(name, type));
                currentMethod.localTypes.put(name, type);
            } else if (currentClass != null) {
                currentClass.fields.put(name, type);
            }
            super.visit(n);
        }
    }

    private static final class CallGraphVisitor extends DepthFirstVisitor {
        private final ProgramModel model;
        private final MethodInfo currentMethod;
        private final Set<MethodInfo> targets;

        CallGraphVisitor(ProgramModel model, MethodInfo currentMethod, Set<MethodInfo> targets) {
            this.model = model;
            this.currentMethod = currentMethod;
            this.targets = targets;
        }

        @Override
        public void visit(MessageSendStatement n) {
            if (n.f1.choice instanceof VoidMessageSendStmt) {
                collect(((VoidMessageSendStmt) n.f1.choice).f0);
            } else {
                collect(((RetMessageSendStmt) n.f1.choice).f2);
            }
            super.visit(n);
        }

        @Override
        public void visit(VoidMessageSendStmt n) {
            collect(n.f0);
            super.visit(n);
        }

        @Override
        public void visit(RetMessageSendStmt n) {
            collect(n.f2);
            super.visit(n);
        }

        private void collect(MessageSend send) {
            if (send == null) {
                return;
            }
            Set<String> receiverTypes = staticTypes(send.f0, currentMethod, model);
            for (String type : receiverTypes) {
                MethodInfo target = model.resolveMethod(type, identifier(send.f2));
                if (target != null) {
                    targets.add(target);
                }
            }
        }
    }

    static Set<String> staticTypes(PrimaryExpression expression, MethodInfo method, ProgramModel model) {
        Node choice = expression.f0.choice;
        if (choice instanceof Identifier) {
            String type = model.declaredVarType(method, identifier((Identifier) choice));
            if (type == null) {
                return Collections.emptySet();
            }
            return model.allConcreteTypes(type);
        }
        if (choice instanceof ThisExpression) {
            return Collections.singleton(method.ownerClass);
        }
        if (choice instanceof AllocationExpression) {
            AllocationExpression alloc = (AllocationExpression) choice;
            return Collections.singleton(identifier(alloc.f1));
        }
        return Collections.emptySet();
    }

    static String typeToString(Type type) {
        if (type.f0.choice instanceof ArrayType) {
            return "int[]";
        }
        if (type.f0.choice instanceof BooleanType) {
            return "boolean";
        }
        if (type.f0.choice instanceof IntegerType) {
            return "int";
        }
        if (type.f0.choice instanceof Identifier) {
            return identifier((Identifier) type.f0.choice);
        }
        return type.f0.choice.toString();
    }

    private static String identifier(Identifier node) {
        return node.f0.toString();
    }

    static boolean isObjectType(String type) {
        return type != null && !"int".equals(type) && !"boolean".equals(type) && !"int[]".equals(type);
    }
}
