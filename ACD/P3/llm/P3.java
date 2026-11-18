import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import syntaxtree.AllocationExpression;
import syntaxtree.ArgList;
import syntaxtree.ArgRest;
import syntaxtree.ArrayAllocationExpression;
import syntaxtree.ArrayAssignmentStatement;
import syntaxtree.ArrayLength;
import syntaxtree.ArrayLookup;
import syntaxtree.ArrayType;
import syntaxtree.AssignmentStatement;
import syntaxtree.Block;
import syntaxtree.BooleanType;
import syntaxtree.ClassDeclaration;
import syntaxtree.ClassExtendsDeclaration;
import syntaxtree.CompareExpression;
import syntaxtree.ConstOrId;
import syntaxtree.Expression;
import syntaxtree.FalseLiteral;
import syntaxtree.FieldAssignmentStatement;
import syntaxtree.FieldRead;
import syntaxtree.ForStatement;
import syntaxtree.FormalParameter;
import syntaxtree.FormalParameterList;
import syntaxtree.FormalParameterRest;
import syntaxtree.Goal;
import syntaxtree.Identifier;
import syntaxtree.IfStatement;
import syntaxtree.IntegerLiteral;
import syntaxtree.IntegerLiteralWithNegSign;
import syntaxtree.IntegerLiteralWithPosSign;
import syntaxtree.IntegerType;
import syntaxtree.MainClass;
import syntaxtree.MessageSend;
import syntaxtree.MethodDeclaration;
import syntaxtree.MinusExpression;
import syntaxtree.Node;
import syntaxtree.NodeChoice;
import syntaxtree.NodeListOptional;
import syntaxtree.NodeOptional;
import syntaxtree.NotExpression;
import syntaxtree.PlainIntegerLiteral;
import syntaxtree.PlusExpression;
import syntaxtree.PrimaryExpression;
import syntaxtree.PrintStatement;
import syntaxtree.Statement;
import syntaxtree.ThisExpression;
import syntaxtree.TimesExpression;
import syntaxtree.TrueLiteral;
import syntaxtree.Type;
import syntaxtree.TypeDeclaration;
import syntaxtree.VarDeclaration;
import syntaxtree.VoidMessageSendStatement;
import syntaxtree.WhileStatement;

public class P3 {
	private static final int MAX_ANALYSIS_ITERS = 30;

	public static void main(String[] args) throws Exception {
		new TACoJavaParser((InputStream) System.in);
		Goal root = TACoJavaParser.Goal();
		Program program = ProgramBuilder.build(root);
		Optimizer optimizer = new Optimizer(program);
		optimizer.optimize();
		System.out.print(program.emit());
	}

	private static final class ProgramBuilder {
		static Program build(Goal goal) {
			Program p = new Program();
			p.mainClassName = id(goal.f0.f1);
			p.mainArgName = id(goal.f0.f11);
			p.mainVars = parseVarDecls(goal.f0.f14);
			p.mainStatements = parseStatements(goal.f0.f15);

			for (int i = 0; i < goal.f1.size(); i++) {
				TypeDeclaration td = (TypeDeclaration) goal.f1.elementAt(i);
				Node c = td.f0.choice;
				ClassDef cls;
				if (c instanceof ClassDeclaration) {
					ClassDeclaration cd = (ClassDeclaration) c;
					cls = new ClassDef(id(cd.f1), null);
					cls.fields.addAll(parseVarDecls(cd.f3));
					cls.methods.addAll(parseMethods(cd.f4, cls.name));
				} else {
					ClassExtendsDeclaration ced = (ClassExtendsDeclaration) c;
					cls = new ClassDef(id(ced.f1), id(ced.f3));
					cls.fields.addAll(parseVarDecls(ced.f5));
					cls.methods.addAll(parseMethods(ced.f6, cls.name));
				}
				p.classes.add(cls);
				p.classMap.put(cls.name, cls);
			}
			return p;
		}

		private static List<MethodDef> parseMethods(NodeListOptional nodes, String owner) {
			List<MethodDef> methods = new ArrayList<>();
			for (int i = 0; i < nodes.size(); i++) {
				MethodDeclaration md = (MethodDeclaration) nodes.elementAt(i);
				MethodDef m = new MethodDef(owner, id(md.f2), parseType(md.f1));
				if (md.f4.present()) {
					FormalParameterList fpl = (FormalParameterList) md.f4.node;
					m.params.add(parseFormal(fpl.f0));
					for (int j = 0; j < fpl.f1.size(); j++) {
						FormalParameterRest rest = (FormalParameterRest) fpl.f1.elementAt(j);
						m.params.add(parseFormal(rest.f1));
					}
				}
				m.locals.addAll(parseVarDecls(md.f7));
				m.statements.addAll(parseStatements(md.f8));
				m.returnValue = parseConstOrId(md.f10);
				methods.add(m);
			}
			return methods;
		}

		private static VarDecl parseFormal(FormalParameter f) {
			return new VarDecl(parseType(f.f0), id(f.f1));
		}

		private static List<VarDecl> parseVarDecls(NodeListOptional nodes) {
			List<VarDecl> vars = new ArrayList<>();
			for (int i = 0; i < nodes.size(); i++) {
				VarDeclaration vd = (VarDeclaration) nodes.elementAt(i);
				vars.add(new VarDecl(parseType(vd.f0), id(vd.f1)));
			}
			return vars;
		}

		private static List<Stmt> parseStatements(NodeListOptional nodes) {
			List<Stmt> out = new ArrayList<>();
			for (int i = 0; i < nodes.size(); i++) {
				out.add(parseStatement((Statement) nodes.elementAt(i)));
			}
			return out;
		}

		private static Stmt parseStatement(Statement st) {
			Node n = st.f0.choice;
			if (n instanceof Block) {
				Block b = (Block) n;
				return new BlockStmt(parseStatements(b.f1));
			}
			if (n instanceof AssignmentStatement) {
				AssignmentStatement as = (AssignmentStatement) n;
				return new AssignStmt(id(as.f0), parseExpr(as.f2));
			}
			if (n instanceof ArrayAssignmentStatement) {
				ArrayAssignmentStatement a = (ArrayAssignmentStatement) n;
				return new ArrayAssignStmt(id(a.f0), parseConstOrId(a.f2), parseConstOrId(a.f5));
			}
			if (n instanceof FieldAssignmentStatement) {
				FieldAssignmentStatement f = (FieldAssignmentStatement) n;
				return new FieldAssignStmt(id(f.f0), id(f.f2), parseConstOrId(f.f4));
			}
			if (n instanceof VoidMessageSendStatement) {
				VoidMessageSendStatement vm = (VoidMessageSendStatement) n;
				return new VoidCallStmt(parseCall(vm.f0));
			}
			if (n instanceof IfStatement) {
				IfStatement i = (IfStatement) n;
				return new IfStmt(id(i.f2), parseStatement(i.f4), parseStatement(i.f6));
			}
			if (n instanceof WhileStatement) {
				WhileStatement w = (WhileStatement) n;
				return new WhileStmt(id(w.f2), parseStatement(w.f4));
			}
			if (n instanceof ForStatement) {
				ForStatement f = (ForStatement) n;
				return new ForStmt(id(f.f2), parseExpr(f.f4), parseExpr(f.f6), id(f.f8), parseExpr(f.f10), parseStatement(f.f12));
			}
			if (n instanceof PrintStatement) {
				PrintStatement p = (PrintStatement) n;
				return new PrintStmt(parseConstOrId(p.f2));
			}
			throw new RuntimeException("Unknown statement node: " + n.getClass().getName());
		}

		private static Expr parseExpr(Expression e) {
			Node n = e.f0.choice;
			if (n instanceof CompareExpression) {
				CompareExpression c = (CompareExpression) n;
				return new BinExpr("<", parseConstOrId(c.f0), parseConstOrId(c.f2));
			}
			if (n instanceof PlusExpression) {
				PlusExpression p = (PlusExpression) n;
				return new BinExpr("+", parseConstOrId(p.f0), parseConstOrId(p.f2));
			}
			if (n instanceof MinusExpression) {
				MinusExpression m = (MinusExpression) n;
				return new BinExpr("-", parseConstOrId(m.f0), parseConstOrId(m.f2));
			}
			if (n instanceof TimesExpression) {
				TimesExpression t = (TimesExpression) n;
				return new BinExpr("*", parseConstOrId(t.f0), parseConstOrId(t.f2));
			}
			if (n instanceof ArrayLookup) {
				ArrayLookup a = (ArrayLookup) n;
				return new ArrayLookupExpr(id(a.f0), parseConstOrId(a.f2));
			}
			if (n instanceof ArrayLength) {
				ArrayLength a = (ArrayLength) n;
				return new ArrayLengthExpr(id(a.f0));
			}
			if (n instanceof MessageSend) {
				return parseCall((MessageSend) n);
			}
			if (n instanceof FieldRead) {
				FieldRead fr = (FieldRead) n;
				return new FieldReadExpr(id(fr.f0), id(fr.f2));
			}
			if (n instanceof PrimaryExpression) {
				return parsePrimary((PrimaryExpression) n);
			}
			throw new RuntimeException("Unknown expression node: " + n.getClass().getName());
		}

		private static CallExpr parseCall(MessageSend ms) {
			Expr recv = parsePrimary(ms.f0);
			List<ValueRef> args = new ArrayList<>();
			if (ms.f4.present()) {
				ArgList al = (ArgList) ms.f4.node;
				args.add(parseConstOrId(al.f0));
				for (int i = 0; i < al.f1.size(); i++) {
					ArgRest ar = (ArgRest) al.f1.elementAt(i);
					args.add(parseConstOrId(ar.f1));
				}
			}
			return new CallExpr(recv, id(ms.f2), args);
		}

		private static Expr parsePrimary(PrimaryExpression p) {
			Node n = p.f0.choice;
			if (n instanceof IntegerLiteral || n instanceof TrueLiteral || n instanceof FalseLiteral || n instanceof Identifier) {
				return new ValueExpr(parsePrimaryValue(n));
			}
			if (n instanceof ThisExpression) {
				return new ThisExpr();
			}
			if (n instanceof ArrayAllocationExpression) {
				ArrayAllocationExpression a = (ArrayAllocationExpression) n;
				return new NewArrayExpr(parseConstOrId(a.f3));
			}
			if (n instanceof AllocationExpression) {
				AllocationExpression a = (AllocationExpression) n;
				return new NewObjectExpr(id(a.f1));
			}
			if (n instanceof NotExpression) {
				NotExpression no = (NotExpression) n;
				return new NotExpr(id(no.f1));
			}
			throw new RuntimeException("Unknown primary expression node: " + n.getClass().getName());
		}

		private static ValueRef parseConstOrId(ConstOrId coi) {
			return parsePrimaryValue(coi.f0.choice);
		}

		private static ValueRef parsePrimaryValue(Node n) {
			if (n instanceof Identifier) {
				return ValueRef.id(id((Identifier) n));
			}
			if (n instanceof TrueLiteral) {
				return ValueRef.bool(true);
			}
			if (n instanceof FalseLiteral) {
				return ValueRef.bool(false);
			}
			if (n instanceof IntegerLiteral) {
				return ValueRef.integer(parseInt((IntegerLiteral) n));
			}
			throw new RuntimeException("Expected ConstOrId but found: " + n.getClass().getName());
		}

		private static int parseInt(IntegerLiteral il) {
			Node n = il.f0.choice;
			if (n instanceof IntegerLiteralWithPosSign) {
				return Integer.parseInt(((IntegerLiteralWithPosSign) n).f1.tokenImage);
			}
			if (n instanceof IntegerLiteralWithNegSign) {
				return -Integer.parseInt(((IntegerLiteralWithNegSign) n).f1.tokenImage);
			}
			return Integer.parseInt(((PlainIntegerLiteral) n).f0.tokenImage);
		}

		private static String parseType(Type t) {
			Node n = t.f0.choice;
			if (n instanceof ArrayType) {
				return "int[]";
			}
			if (n instanceof BooleanType) {
				return "boolean";
			}
			if (n instanceof IntegerType) {
				return "int";
			}
			return id((Identifier) n);
		}

		private static String id(Identifier i) {
			return i.f0.tokenImage;
		}
	}

	private static final class Optimizer {
		private final Program p;
		private final Map<String, Set<String>> children = new HashMap<>();
		private final Map<String, Set<String>> allSubtypes = new HashMap<>();
		private final Map<String, MethodDef> methodByKey = new LinkedHashMap<>();
		private final Map<String, Set<String>> classMethods = new HashMap<>();
		private final Set<String> instantiatedClasses = new HashSet<>();
		private final Map<String, Boolean> methodImpure = new HashMap<>();
		private final Map<String, LVal> methodReturn = new HashMap<>();
		private final Map<String, List<LVal>> paramIn = new HashMap<>();
		private final Set<String> reachableMethodKeys = new HashSet<>();
		private final Set<String> directlyRecursiveMethods = new HashSet<>();
		private final Map<String, Boolean> calledFromOtherMethod = new HashMap<>();
		private final Map<String, Integer> methodNameCallCount = new HashMap<>();
		private final Map<String, Integer> mainVarAssignCount = new HashMap<>();
		private final Set<String> originalInstantiatedClasses = new HashSet<>();
		private final Set<String> originalMainWhileCondVars = new HashSet<>();
		private final Map<String, Integer> mainMethodNameCallCount = new HashMap<>();
		private String oneShotRootMethodKey = null;

		Optimizer(Program p) {
			this.p = p;
			collectOriginalInstantiatedClasses(p.mainStatements, originalInstantiatedClasses);
			for (ClassDef c : p.classes) {
				for (MethodDef m : c.methods) {
					collectOriginalInstantiatedClasses(m.statements, originalInstantiatedClasses);
				}
			}
			collectOriginalMainWhileCondVars(p.mainStatements, originalMainWhileCondVars);
			reindexProgram();
		}

		private void collectOriginalInstantiatedClasses(List<Stmt> statements, Set<String> out) {
			for (Stmt s : statements) {
				collectOriginalInstantiatedClassesFromStmt(s, out);
			}
		}

		private void collectOriginalInstantiatedClassesFromStmt(Stmt s, Set<String> out) {
			if (s instanceof BlockStmt) {
				for (Stmt st : ((BlockStmt) s).statements) {
					collectOriginalInstantiatedClassesFromStmt(st, out);
				}
				return;
			}
			if (s instanceof AssignStmt) {
				collectOriginalInstantiatedClassesFromExpr(((AssignStmt) s).expr, out);
				return;
			}
			if (s instanceof VoidCallStmt) {
				collectOriginalInstantiatedClassesFromExpr(((VoidCallStmt) s).call, out);
				return;
			}
			if (s instanceof IfStmt) {
				IfStmt i = (IfStmt) s;
				collectOriginalInstantiatedClassesFromStmt(i.thenStmt, out);
				collectOriginalInstantiatedClassesFromStmt(i.elseStmt, out);
				return;
			}
			if (s instanceof WhileStmt) {
				collectOriginalInstantiatedClassesFromStmt(((WhileStmt) s).body, out);
				return;
			}
			if (s instanceof ForStmt) {
				ForStmt f = (ForStmt) s;
				collectOriginalInstantiatedClassesFromExpr(f.initExpr, out);
				collectOriginalInstantiatedClassesFromExpr(f.condExpr, out);
				collectOriginalInstantiatedClassesFromExpr(f.stepExpr, out);
				collectOriginalInstantiatedClassesFromStmt(f.body, out);
			}
		}

		private void collectOriginalInstantiatedClassesFromExpr(Expr e, Set<String> out) {
			if (e instanceof NewObjectExpr) {
				out.add(((NewObjectExpr) e).className);
				return;
			}
			if (e instanceof CallExpr) {
				collectOriginalInstantiatedClassesFromExpr(((CallExpr) e).receiver, out);
			}
		}

		private void collectOriginalMainWhileCondVars(List<Stmt> statements, Set<String> out) {
			for (Stmt s : statements) {
				collectOriginalMainWhileCondVarsFromStmt(s, out);
			}
		}

		private void collectOriginalMainWhileCondVarsFromStmt(Stmt s, Set<String> out) {
			if (s instanceof BlockStmt) {
				for (Stmt st : ((BlockStmt) s).statements) {
					collectOriginalMainWhileCondVarsFromStmt(st, out);
				}
				return;
			}
			if (s instanceof WhileStmt) {
				WhileStmt w = (WhileStmt) s;
				out.add(w.condVar);
				collectOriginalMainWhileCondVarsFromStmt(w.body, out);
				return;
			}
			if (s instanceof IfStmt) {
				IfStmt i = (IfStmt) s;
				collectOriginalMainWhileCondVarsFromStmt(i.thenStmt, out);
				collectOriginalMainWhileCondVarsFromStmt(i.elseStmt, out);
				return;
			}
			if (s instanceof ForStmt) {
				collectOriginalMainWhileCondVarsFromStmt(((ForStmt) s).body, out);
			}
		}

		void optimize() {
			for (int round = 0; round < 8; round++) {
				String before = p.emit();

				reindexProgram();
				collectInstantiatedClasses();
				buildSubtypeIndex();
				initAnalysisState();
				analyzePurity();
				analyzeInterproceduralConstants();

				optimizeMainBody();
				for (MethodDef m : methodByKey.values()) {
					optimizeMethodBody(m);
				}

				// One more local clean-up round after call/branch rewrites.
				optimizeMainBody();
				for (MethodDef m : methodByKey.values()) {
					optimizeMethodBody(m);
				}

				pruneUncalledMethods();
				pruneUnreachableClasses();
				dropUnusedVarDecls();

				String after = p.emit();
				if (before.equals(after)) {
					break;
				}
			}
			reindexProgram();
			collectInstantiatedClasses();
			buildSubtypeIndex();
		}

		private void reindexProgram() {
			methodByKey.clear();
			classMethods.clear();
			directlyRecursiveMethods.clear();
			calledFromOtherMethod.clear();
			methodNameCallCount.clear();
			mainVarAssignCount.clear();
			mainMethodNameCallCount.clear();
			p.classMap.clear();
			for (ClassDef c : p.classes) {
				p.classMap.put(c.name, c);
			}
			indexProgram();
			for (Stmt s : p.mainStatements) {
				collectMainAssignCounts(s, mainVarAssignCount);
				collectCalledMethodNamesFromStmt(s, methodNameCallCount);
				collectCalledMethodNamesFromStmt(s, mainMethodNameCallCount);
			}
			Map<String, List<String>> methodKeysByName = new HashMap<>();
			for (MethodDef m : methodByKey.values()) {
				calledFromOtherMethod.put(m.key(), false);
				methodKeysByName.computeIfAbsent(m.name, k -> new ArrayList<String>()).add(m.key());
			}
			for (MethodDef caller : methodByKey.values()) {
				for (Stmt s : caller.statements) {
					collectCalledMethodNamesFromStmt(s, methodNameCallCount);
				}
				Set<String> calledNames = collectCalledMethodNames(caller.statements);
				for (String calledName : calledNames) {
					for (String targetKey : methodKeysByName.getOrDefault(calledName, Collections.emptyList())) {
						if (!targetKey.equals(caller.key())) {
							calledFromOtherMethod.put(targetKey, true);
						}
					}
				}
			}
			for (MethodDef m : methodByKey.values()) {
				if (containsCallToMethodName(m.statements, m.name)) {
					directlyRecursiveMethods.add(m.key());
				}
			}
		}

		private void collectMainAssignCounts(Stmt s, Map<String, Integer> out) {
			if (s instanceof BlockStmt) {
				for (Stmt st : ((BlockStmt) s).statements) {
					collectMainAssignCounts(st, out);
				}
				return;
			}
			if (s instanceof AssignStmt) {
				AssignStmt a = (AssignStmt) s;
				out.put(a.lhs, out.getOrDefault(a.lhs, 0) + 1);
				return;
			}
			if (s instanceof IfStmt) {
				IfStmt i = (IfStmt) s;
				collectMainAssignCounts(i.thenStmt, out);
				collectMainAssignCounts(i.elseStmt, out);
				return;
			}
			if (s instanceof WhileStmt) {
				collectMainAssignCounts(((WhileStmt) s).body, out);
				return;
			}
			if (s instanceof ForStmt) {
				ForStmt f = (ForStmt) s;
				out.put(f.initVar, out.getOrDefault(f.initVar, 0) + 1);
				out.put(f.stepVar, out.getOrDefault(f.stepVar, 0) + 1);
				collectMainAssignCounts(f.body, out);
			}
		}

		private Set<String> collectCalledMethodNames(List<Stmt> statements) {
			Set<String> out = new HashSet<>();
			for (Stmt s : statements) {
				collectCalledMethodNamesFromStmt(s, out);
			}
			return out;
		}

		private void collectCalledMethodNamesFromStmt(Stmt s, Set<String> out) {
			if (s instanceof BlockStmt) {
				for (Stmt st : ((BlockStmt) s).statements) {
					collectCalledMethodNamesFromStmt(st, out);
				}
				return;
			}
			if (s instanceof AssignStmt) {
				collectCalledMethodNamesFromExpr(((AssignStmt) s).expr, out);
				return;
			}
			if (s instanceof VoidCallStmt) {
				collectCalledMethodNamesFromExpr(((VoidCallStmt) s).call, out);
				return;
			}
			if (s instanceof IfStmt) {
				IfStmt i = (IfStmt) s;
				collectCalledMethodNamesFromStmt(i.thenStmt, out);
				collectCalledMethodNamesFromStmt(i.elseStmt, out);
				return;
			}
			if (s instanceof WhileStmt) {
				collectCalledMethodNamesFromStmt(((WhileStmt) s).body, out);
				return;
			}
			if (s instanceof ForStmt) {
				ForStmt f = (ForStmt) s;
				collectCalledMethodNamesFromExpr(f.initExpr, out);
				collectCalledMethodNamesFromExpr(f.condExpr, out);
				collectCalledMethodNamesFromExpr(f.stepExpr, out);
				collectCalledMethodNamesFromStmt(f.body, out);
			}
		}

		private void collectCalledMethodNamesFromStmt(Stmt s, Map<String, Integer> out) {
			if (s instanceof BlockStmt) {
				for (Stmt st : ((BlockStmt) s).statements) {
					collectCalledMethodNamesFromStmt(st, out);
				}
				return;
			}
			if (s instanceof AssignStmt) {
				collectCalledMethodNamesFromExpr(((AssignStmt) s).expr, out);
				return;
			}
			if (s instanceof VoidCallStmt) {
				collectCalledMethodNamesFromExpr(((VoidCallStmt) s).call, out);
				return;
			}
			if (s instanceof IfStmt) {
				IfStmt i = (IfStmt) s;
				collectCalledMethodNamesFromStmt(i.thenStmt, out);
				collectCalledMethodNamesFromStmt(i.elseStmt, out);
				return;
			}
			if (s instanceof WhileStmt) {
				collectCalledMethodNamesFromStmt(((WhileStmt) s).body, out);
				return;
			}
			if (s instanceof ForStmt) {
				ForStmt f = (ForStmt) s;
				collectCalledMethodNamesFromExpr(f.initExpr, out);
				collectCalledMethodNamesFromExpr(f.condExpr, out);
				collectCalledMethodNamesFromExpr(f.stepExpr, out);
				collectCalledMethodNamesFromStmt(f.body, out);
			}
		}

		private void collectCalledMethodNamesFromExpr(Expr e, Set<String> out) {
			if (e instanceof CallExpr) {
				CallExpr c = (CallExpr) e;
				out.add(c.method);
				collectCalledMethodNamesFromExpr(c.receiver, out);
			}
		}

		private void collectCalledMethodNamesFromExpr(Expr e, Map<String, Integer> out) {
			if (e instanceof CallExpr) {
				CallExpr c = (CallExpr) e;
				out.put(c.method, out.getOrDefault(c.method, 0) + 1);
				collectCalledMethodNamesFromExpr(c.receiver, out);
			}
		}

		private boolean containsCallToMethodName(List<Stmt> statements, String methodName) {
			for (Stmt s : statements) {
				if (stmtContainsCallToMethodName(s, methodName)) {
					return true;
				}
			}
			return false;
		}

		private boolean stmtContainsCallToMethodName(Stmt s, String methodName) {
			if (s instanceof BlockStmt) {
				for (Stmt st : ((BlockStmt) s).statements) {
					if (stmtContainsCallToMethodName(st, methodName)) {
						return true;
					}
				}
				return false;
			}
			if (s instanceof AssignStmt) {
				return exprContainsCallToMethodName(((AssignStmt) s).expr, methodName);
			}
			if (s instanceof VoidCallStmt) {
				return exprContainsCallToMethodName(((VoidCallStmt) s).call, methodName);
			}
			if (s instanceof IfStmt) {
				IfStmt i = (IfStmt) s;
				return stmtContainsCallToMethodName(i.thenStmt, methodName) || stmtContainsCallToMethodName(i.elseStmt, methodName);
			}
			if (s instanceof WhileStmt) {
				return stmtContainsCallToMethodName(((WhileStmt) s).body, methodName);
			}
			if (s instanceof ForStmt) {
				ForStmt f = (ForStmt) s;
				return exprContainsCallToMethodName(f.initExpr, methodName) || exprContainsCallToMethodName(f.condExpr, methodName) || exprContainsCallToMethodName(f.stepExpr, methodName) || stmtContainsCallToMethodName(f.body, methodName);
			}
			return false;
		}

		private boolean exprContainsCallToMethodName(Expr e, String methodName) {
			if (e instanceof CallExpr) {
				CallExpr c = (CallExpr) e;
				if (methodName.equals(c.method)) {
					return true;
				}
				return exprContainsCallToMethodName(c.receiver, methodName);
			}
			return false;
		}

		private void indexProgram() {
			for (ClassDef c : p.classes) {
				classMethods.put(c.name, new HashSet<String>());
				for (MethodDef m : c.methods) {
					String key = m.key();
					methodByKey.put(key, m);
					classMethods.get(c.name).add(m.name);
				}
			}
		}

		private void collectInstantiatedClasses() {
			instantiatedClasses.clear();
			collectNewsFromStatements(p.mainStatements);
			for (MethodDef m : methodByKey.values()) {
				collectNewsFromStatements(m.statements);
			}
		}

		private void collectNewsFromStatements(List<Stmt> statements) {
			for (Stmt s : statements) {
				collectNewsFromStmt(s);
			}
		}

		private void collectNewsFromStmt(Stmt s) {
			if (s instanceof BlockStmt) {
				collectNewsFromStatements(((BlockStmt) s).statements);
				return;
			}
			if (s instanceof AssignStmt) {
				collectNewsFromExpr(((AssignStmt) s).expr);
				return;
			}
			if (s instanceof ArrayAssignStmt) {
				return;
			}
			if (s instanceof FieldAssignStmt) {
				return;
			}
			if (s instanceof VoidCallStmt) {
				collectNewsFromExpr(((VoidCallStmt) s).call);
				return;
			}
			if (s instanceof IfStmt) {
				IfStmt i = (IfStmt) s;
				collectNewsFromStmt(i.thenStmt);
				collectNewsFromStmt(i.elseStmt);
				return;
			}
			if (s instanceof WhileStmt) {
				collectNewsFromStmt(((WhileStmt) s).body);
				return;
			}
			if (s instanceof ForStmt) {
				ForStmt f = (ForStmt) s;
				collectNewsFromExpr(f.initExpr);
				collectNewsFromExpr(f.condExpr);
				collectNewsFromExpr(f.stepExpr);
				collectNewsFromStmt(f.body);
			}
		}

		private void collectNewsFromExpr(Expr e) {
			if (e instanceof NewObjectExpr) {
				instantiatedClasses.add(((NewObjectExpr) e).className);
				return;
			}
			if (e instanceof CallExpr) {
				CallExpr c = (CallExpr) e;
				collectNewsFromExpr(c.receiver);
				return;
			}
			if (e instanceof NewArrayExpr || e instanceof NotExpr || e instanceof ValueExpr || e instanceof ThisExpr || e instanceof ArrayLengthExpr || e instanceof FieldReadExpr || e instanceof ArrayLookupExpr) {
				return;
			}
			if (e instanceof BinExpr) {
				return;
			}
		}

		private void buildSubtypeIndex() {
			children.clear();
			allSubtypes.clear();
			for (ClassDef c : p.classes) {
				children.putIfAbsent(c.name, new HashSet<String>());
			}
			for (ClassDef c : p.classes) {
				if (c.parent != null) {
					children.putIfAbsent(c.parent, new HashSet<String>());
					children.get(c.parent).add(c.name);
				}
			}
			for (ClassDef c : p.classes) {
				Set<String> subs = new HashSet<>();
				Deque<String> q = new ArrayDeque<>();
				q.add(c.name);
				while (!q.isEmpty()) {
					String cur = q.removeFirst();
					if (!subs.add(cur)) {
						continue;
					}
					for (String ch : children.getOrDefault(cur, Collections.emptySet())) {
						q.addLast(ch);
					}
				}
				allSubtypes.put(c.name, subs);
			}
		}

		private void initAnalysisState() {
			methodImpure.clear();
			methodReturn.clear();
			paramIn.clear();
			for (MethodDef m : methodByKey.values()) {
				methodImpure.put(m.key(), false);
				methodReturn.put(m.key(), LVal.undef());
				List<LVal> init = new ArrayList<>();
				for (int i = 0; i < m.params.size(); i++) {
					init.add(LVal.undef());
				}
				paramIn.put(m.key(), init);
			}
		}

		private void analyzePurity() {
			boolean changed;
			int iter = 0;
			do {
				changed = false;
				iter++;
				for (MethodDef m : methodByKey.values()) {
					boolean imp = methodIsImpure(m);
					String key = m.key();
					if (imp != methodImpure.get(key)) {
						methodImpure.put(key, imp);
						changed = true;
					}
				}
			} while (changed && iter < MAX_ANALYSIS_ITERS);
		}

		private boolean methodIsImpure(MethodDef m) {
			for (Stmt s : m.statements) {
				if (stmtHasSideEffect(s, m)) {
					return true;
				}
			}
			return exprHasImpureCall(valueToExpr(m.returnValue), m);
		}

		private boolean stmtHasSideEffect(Stmt s, MethodDef current) {
			if (s instanceof BlockStmt) {
				for (Stmt inner : ((BlockStmt) s).statements) {
					if (stmtHasSideEffect(inner, current)) {
						return true;
					}
				}
				return false;
			}
			if (s instanceof AssignStmt) {
				return exprHasImpureCall(((AssignStmt) s).expr, current);
			}
			if (s instanceof ArrayAssignStmt || s instanceof FieldAssignStmt || s instanceof PrintStmt) {
				return true;
			}
			if (s instanceof VoidCallStmt) {
				return exprHasImpureCall(((VoidCallStmt) s).call, current);
			}
			if (s instanceof IfStmt) {
				IfStmt i = (IfStmt) s;
				return stmtHasSideEffect(i.thenStmt, current) || stmtHasSideEffect(i.elseStmt, current);
			}
			if (s instanceof WhileStmt) {
				return stmtHasSideEffect(((WhileStmt) s).body, current);
			}
			if (s instanceof ForStmt) {
				ForStmt f = (ForStmt) s;
				return exprHasImpureCall(f.initExpr, current) || exprHasImpureCall(f.condExpr, current) || exprHasImpureCall(f.stepExpr, current) || stmtHasSideEffect(f.body, current);
			}
			return false;
		}

		private boolean exprHasImpureCall(Expr e, MethodDef current) {
			if (e instanceof CallExpr) {
				CallExpr c = (CallExpr) e;
				if (exprHasImpureCall(c.receiver, current)) {
					return true;
				}
				Set<String> targets = resolveDispatchTargets(c, current, true);
				if (targets.isEmpty()) {
					return true;
				}
				for (String t : targets) {
					if (methodImpure.getOrDefault(t, false)) {
						return true;
					}
				}
				return false;
			}
			if (e instanceof NewArrayExpr || e instanceof NewObjectExpr) {
				return current == null;
			}
			if (e instanceof BinExpr || e instanceof ArrayLookupExpr || e instanceof ArrayLengthExpr || e instanceof FieldReadExpr || e instanceof ValueExpr || e instanceof ThisExpr || e instanceof NotExpr) {
				return false;
			}
			return false;
		}

		private void analyzeInterproceduralConstants() {
			boolean changed;
			int iter = 0;
			do {
				changed = false;
				iter++;

				Map<String, List<LVal>> newParamFacts = new HashMap<>();
				for (MethodDef m : methodByKey.values()) {
					List<LVal> vals = new ArrayList<>();
					for (int i = 0; i < m.params.size(); i++) {
						vals.add(LVal.undef());
					}
					newParamFacts.put(m.key(), vals);
				}

				analyzeMainForCalls(newParamFacts);

				for (MethodDef m : methodByKey.values()) {
					AnalysisResult r = analyzeMethod(m, paramIn.get(m.key()), false, newParamFacts);
					LVal oldRet = methodReturn.get(m.key());
					LVal newRet = LVal.meet(oldRet, r.returnValue);
					if (!newRet.equals(oldRet)) {
						methodReturn.put(m.key(), newRet);
						changed = true;
					}
				}

				for (MethodDef m : methodByKey.values()) {
					String key = m.key();
					List<LVal> oldVals = paramIn.get(key);
					List<LVal> addVals = newParamFacts.get(key);
					for (int i = 0; i < oldVals.size(); i++) {
						LVal merged = LVal.meet(oldVals.get(i), addVals.get(i));
						if (!merged.equals(oldVals.get(i))) {
							oldVals.set(i, merged);
							changed = true;
						}
					}
				}
			} while (changed && iter < MAX_ANALYSIS_ITERS);
		}

		private void analyzeMainForCalls(Map<String, List<LVal>> newParamFacts) {
			Env env = new Env();
			for (VarDecl v : p.mainVars) {
				env.types.put(v.name, v.type);
				env.state.put(v.name, LVal.undef());
			}
			walkStatementsForFacts(p.mainStatements, env, null, newParamFacts);
		}

		private AnalysisResult analyzeMethod(MethodDef m, List<LVal> inParams, boolean rewrite, Map<String, List<LVal>> newParamFacts) {
			Env env = new Env();
			env.currentClass = m.owner;
			env.currentMethod = m;
			bindTypes(env, m);
			for (int i = 0; i < m.params.size(); i++) {
				env.state.put(m.params.get(i).name, inParams.get(i));
			}
			for (VarDecl local : m.locals) {
				env.state.putIfAbsent(local.name, LVal.undef());
			}
			List<Stmt> rewritten = walkStatementsForFacts(m.statements, env, m, newParamFacts);

			ValueRef rv = m.returnValue;
			ValueRef newRv = substitute(rv, env.state);
			LVal retVal = valOf(newRv, env.state);
			if (rewrite) {
				m.statements = rewritten;
				m.returnValue = newRv;
			}
			return new AnalysisResult(retVal);
		}

		private List<Stmt> walkStatementsForFacts(List<Stmt> statements, Env env, MethodDef method, Map<String, List<LVal>> newParamFacts) {
			List<Stmt> out = new ArrayList<>();
			for (Stmt s : statements) {
				Stmt n = transferStmt(s, env, method, newParamFacts);
				if (n != null) {
					if (n instanceof BlockStmt) {
						List<Stmt> inner = ((BlockStmt) n).statements;
						if (inner.isEmpty()) {
							continue;
						}
						out.addAll(inner);
					} else {
						out.add(n);
					}
				}
			}
			return out;
		}

		private Stmt transferStmt(Stmt s, Env env, MethodDef method, Map<String, List<LVal>> newParamFacts) {
			if (s instanceof BlockStmt) {
				List<Stmt> body = walkStatementsForFacts(((BlockStmt) s).statements, env.copy(), method, newParamFacts);
				env.state.putAll(env.copyBack(body, method, this, newParamFacts));
				return new BlockStmt(body);
			}
			if (s instanceof AssignStmt) {
				AssignStmt a = (AssignStmt) s;
				Expr e = rewriteExpr(a.expr, env, method, newParamFacts);
				LVal v = evalExpr(e, env, method, newParamFacts);
				env.state.put(a.lhs, v);
				return new AssignStmt(a.lhs, e);
			}
			if (s instanceof ArrayAssignStmt) {
				ArrayAssignStmt a = (ArrayAssignStmt) s;
				ValueRef idx = substitute(a.index, env.state);
				ValueRef val = substitute(a.value, env.state);
				env.state.remove(a.array);
				return new ArrayAssignStmt(a.array, idx, val);
			}
			if (s instanceof FieldAssignStmt) {
				FieldAssignStmt f = (FieldAssignStmt) s;
				ValueRef val = substitute(f.value, env.state);
				return new FieldAssignStmt(f.obj, f.field, val);
			}
			if (s instanceof VoidCallStmt) {
				Expr rewritten = rewriteExpr(((VoidCallStmt) s).call, env, method, newParamFacts);
				if (rewritten instanceof CallExpr) {
					CallExpr c = (CallExpr) rewritten;
					evalCall(c, env, method, newParamFacts);
					return new VoidCallStmt(c);
				}
				// Pure void call may fold away to a constant expression; drop it.
				return null;
			}
			if (s instanceof PrintStmt) {
				PrintStmt pStmt = (PrintStmt) s;
				ValueRef v = substitute(pStmt.value, env.state);
				return new PrintStmt(v);
			}
			if (s instanceof IfStmt) {
				IfStmt i = (IfStmt) s;
				LVal cond = env.state.getOrDefault(i.condVar, LVal.top());
				if (cond.isBoolConst(true)) {
					return transferStmt(i.thenStmt, env, method, newParamFacts);
				}
				if (cond.isBoolConst(false)) {
					return transferStmt(i.elseStmt, env, method, newParamFacts);
				}
				Env thenEnv = env.copy();
				Env elseEnv = env.copy();
				Stmt thenStmt = transferStmt(i.thenStmt, thenEnv, method, newParamFacts);
				Stmt elseStmt = transferStmt(i.elseStmt, elseEnv, method, newParamFacts);
				env.state = meetStates(thenEnv.state, elseEnv.state);
				if (isNoOp(thenStmt) && isNoOp(elseStmt)) {
					return null;
				}
				return new IfStmt(i.condVar, nonNullStmt(thenStmt), nonNullStmt(elseStmt));
			}
			if (s instanceof WhileStmt) {
				WhileStmt w = (WhileStmt) s;
				LVal cond = env.state.getOrDefault(w.condVar, LVal.top());
				if (cond.isBoolConst(false)) {
					return null;
				}
				Map<String, LVal> in = new HashMap<>(env.state);
				Map<String, LVal> cur = new HashMap<>(in);
				Stmt rewrittenBody = w.body;
				for (int it = 0; it < 6; it++) {
					Env bodyEnv = env.copyWith(cur);
					rewrittenBody = transferStmt(w.body, bodyEnv, method, newParamFacts);
					Map<String, LVal> next = meetStates(in, bodyEnv.state);
					if (next.equals(cur)) {
						break;
					}
					cur = next;
				}
				env.state = cur;
				return new WhileStmt(w.condVar, nonNullStmt(rewrittenBody));
			}
			if (s instanceof ForStmt) {
				ForStmt f = (ForStmt) s;
				Expr init = rewriteExpr(f.initExpr, env, method, newParamFacts);
				env.state.put(f.initVar, evalExpr(init, env, method, newParamFacts));

				Map<String, LVal> savedState = env.state;
				Map<String, LVal> condState = new HashMap<>(savedState);
				condState.put(f.initVar, LVal.top());
				condState.put(f.stepVar, LVal.top());
				env.state = condState;
				Expr condExpr = rewriteExpr(f.condExpr, env, method, newParamFacts);
				env.state = savedState;
				LVal cond = evalExpr(condExpr, env, method, newParamFacts);
				if (cond.isBoolConst(false)) {
					return null;
				}

				Map<String, LVal> in = new HashMap<>(env.state);
				Map<String, LVal> cur = new HashMap<>(in);
				Stmt rewrittenBody = f.body;
				Expr rewrittenStep = f.stepExpr;
				for (int it = 0; it < 6; it++) {
					Env bodyEnv = env.copyWith(cur);
					rewrittenBody = transferStmt(f.body, bodyEnv, method, newParamFacts);
					rewrittenStep = rewriteExpr(f.stepExpr, bodyEnv, method, newParamFacts);
					bodyEnv.state.put(f.stepVar, evalExpr(rewrittenStep, bodyEnv, method, newParamFacts));
					Map<String, LVal> next = meetStates(in, bodyEnv.state);
					if (next.equals(cur)) {
						break;
					}
					cur = next;
				}
				env.state = cur;
				return new ForStmt(f.initVar, init, condExpr, f.stepVar, rewrittenStep, nonNullStmt(rewrittenBody));
			}
			return s;
		}

		private Stmt nonNullStmt(Stmt s) {
			if (s == null) {
				return new BlockStmt(new ArrayList<Stmt>());
			}
			return s;
		}

		private boolean isNoOp(Stmt s) {
			if (s == null) {
				return true;
			}
			if (s instanceof BlockStmt) {
				BlockStmt b = (BlockStmt) s;
				if (b.statements.isEmpty()) {
					return true;
				}
				for (Stmt st : b.statements) {
					if (!isNoOp(st)) {
						return false;
					}
				}
				return true;
			}
			return false;
		}

		private Map<String, LVal> meetStates(Map<String, LVal> a, Map<String, LVal> b) {
			Map<String, LVal> out = new HashMap<>();
			Set<String> keys = new HashSet<>();
			keys.addAll(a.keySet());
			keys.addAll(b.keySet());
			for (String k : keys) {
				out.put(k, LVal.meet(a.getOrDefault(k, LVal.undef()), b.getOrDefault(k, LVal.undef())));
			}
			return out;
		}

		private Expr rewriteExpr(Expr expr, Env env, MethodDef method, Map<String, List<LVal>> newParamFacts) {
			if (expr instanceof ValueExpr) {
				return new ValueExpr(substitute(((ValueExpr) expr).value, env.state));
			}
			if (expr instanceof BinExpr) {
				BinExpr b = (BinExpr) expr;
				ValueRef l = substitute(b.left, env.state);
				ValueRef r = substitute(b.right, env.state);
				LVal v = evalBin(b.op, valOf(l, env.state), valOf(r, env.state));
				if (v.isConst()) {
					return new ValueExpr(v.toValueRef());
				}
				return new BinExpr(b.op, l, r);
			}
			if (expr instanceof ArrayLookupExpr) {
				ArrayLookupExpr a = (ArrayLookupExpr) expr;
				return new ArrayLookupExpr(a.array, substitute(a.index, env.state));
			}
			if (expr instanceof ArrayLengthExpr || expr instanceof FieldReadExpr || expr instanceof ThisExpr) {
				return expr;
			}
			if (expr instanceof NewArrayExpr) {
				NewArrayExpr n = (NewArrayExpr) expr;
				return new NewArrayExpr(substitute(n.size, env.state));
			}
			if (expr instanceof NewObjectExpr) {
				return expr;
			}
			if (expr instanceof NotExpr) {
				NotExpr n = (NotExpr) expr;
				LVal v = env.state.getOrDefault(n.id, LVal.top());
				if (v.isBoolConst(true)) {
					return new ValueExpr(ValueRef.bool(false));
				}
				if (v.isBoolConst(false)) {
					return new ValueExpr(ValueRef.bool(true));
				}
				return expr;
			}
			if (expr instanceof CallExpr) {
				CallExpr c = (CallExpr) expr;
				Expr recv = rewriteExpr(c.receiver, env, method, newParamFacts);
				List<ValueRef> args = new ArrayList<>();
				for (ValueRef a : c.args) {
					args.add(substitute(a, env.state));
				}
				CallExpr rebuilt = new CallExpr(recv, c.method, args);
				LVal rv = evalCall(rebuilt, env, method, newParamFacts);
				if (!rv.isConst() && method == null) {
					LVal precise = trySingleTargetMainCall(rebuilt, env);
					if (precise.isConst()) {
						rv = precise;
					}
				}
				if (rv.isConst() && !exprHasImpureCall(rebuilt, method) && canFoldCallToConst(rebuilt, method)) {
					return new ValueExpr(rv.toValueRef());
				}
				return rebuilt;
			}
			return expr;
		}

		private boolean canFoldCallToConst(CallExpr call, MethodDef method) {
			if (method == null && call.receiver instanceof ValueExpr) {
				ValueRef rv = ((ValueExpr) call.receiver).value;
				if (rv.kind == RefKind.ID && mainVarAssignCount.getOrDefault(rv.id, 0) > 1) {
					return false;
				}
			}
			return true;
		}

		private LVal evalExpr(Expr expr, Env env, MethodDef method, Map<String, List<LVal>> newParamFacts) {
			if (expr instanceof ValueExpr) {
				return valOf(((ValueExpr) expr).value, env.state);
			}
			if (expr instanceof BinExpr) {
				BinExpr b = (BinExpr) expr;
				return evalBin(b.op, valOf(b.left, env.state), valOf(b.right, env.state));
			}
			if (expr instanceof NotExpr) {
				LVal v = env.state.getOrDefault(((NotExpr) expr).id, LVal.top());
				if (v.isBoolConst(true)) {
					return LVal.bool(false);
				}
				if (v.isBoolConst(false)) {
					return LVal.bool(true);
				}
				return LVal.top();
			}
			if (expr instanceof CallExpr) {
				return evalCall((CallExpr) expr, env, method, newParamFacts);
			}
			return LVal.top();
		}

		private LVal evalCall(CallExpr call, Env env, MethodDef method, Map<String, List<LVal>> newParamFacts) {
			Set<String> targets = resolveDispatchTargets(call, method, true);
			if (targets.isEmpty()) {
				return LVal.top();
			}

			List<LVal> argVals = new ArrayList<>();
			for (ValueRef a : call.args) {
				argVals.add(valOf(a, env.state));
			}

			LVal ret = LVal.undef();
			for (String t : targets) {
				MethodDef target = methodByKey.get(t);
				if (target != null) {
					List<LVal> acc = newParamFacts.get(t);
					if (acc != null) {
						for (int i = 0; i < Math.min(acc.size(), argVals.size()); i++) {
							acc.set(i, LVal.meet(acc.get(i), argVals.get(i)));
						}
					}
				}
				if (oneShotRootMethodKey != null && oneShotRootMethodKey.equals(t)) {
					ret = LVal.meet(ret, LVal.top());
					continue;
				}
				if (directlyRecursiveMethods.contains(t)) {
					ret = LVal.meet(ret, LVal.top());
					continue;
				}
				ret = LVal.meet(ret, methodReturn.getOrDefault(t, LVal.top()));
			}
			return ret;
		}

		private LVal trySingleTargetMainCall(CallExpr call, Env env) {
			Set<String> targets = resolveDispatchTargets(call, null, true);
			if (targets.size() != 1) {
				return LVal.top();
			}

			String key = targets.iterator().next();
			MethodDef target = methodByKey.get(key);
			if (target == null || target.params.size() != call.args.size()) {
				return LVal.top();
			}
			if (calledFromOtherMethod.getOrDefault(key, false)) {
				return LVal.top();
			}
			if (mainMethodNameCallCount.getOrDefault(target.name, 0) > 1) {
				return LVal.top();
			}
			if (call.receiver instanceof ValueExpr) {
				ValueRef rv = ((ValueExpr) call.receiver).value;
				if (rv.kind == RefKind.ID && mainVarAssignCount.getOrDefault(rv.id, 0) > 1) {
					return LVal.top();
				}
			}

			List<LVal> actuals = new ArrayList<>();
			for (ValueRef arg : call.args) {
				LVal v = valOf(arg, env.state);
				if (!v.isConst()) {
					return LVal.top();
				}
				actuals.add(v);
			}

			String prev = oneShotRootMethodKey;
			oneShotRootMethodKey = key;
			try {
				AnalysisResult oneShot = analyzeMethod(target, actuals, false, freshParamFacts());
				return oneShot.returnValue;
			} finally {
				oneShotRootMethodKey = prev;
			}
		}

		private LVal evalBin(String op, LVal a, LVal b) {
			if (!a.isConst() || !b.isConst()) {
				return LVal.top();
			}
			if (("+".equals(op) || "-".equals(op) || "*".equals(op) || "<".equals(op)) && (!a.isIntConst() || !b.isIntConst())) {
				return LVal.top();
			}
			int ai;
			int bi;
			switch (op) {
				case "+":
					ai = ((Integer) a.value);
					bi = ((Integer) b.value);
					return LVal.integer(ai + bi);
				case "-":
					ai = ((Integer) a.value);
					bi = ((Integer) b.value);
					return LVal.integer(ai - bi);
				case "*":
					ai = ((Integer) a.value);
					bi = ((Integer) b.value);
					return LVal.integer(ai * bi);
				case "<":
					ai = ((Integer) a.value);
					bi = ((Integer) b.value);
					return LVal.bool(ai < bi);
				default:
					return LVal.top();
			}
		}

		private ValueRef substitute(ValueRef v, Map<String, LVal> state) {
			if (v.kind != RefKind.ID) {
				return v;
			}
			LVal lv = state.getOrDefault(v.id, LVal.top());
			if (lv.isConst()) {
				return lv.toValueRef();
			}
			return v;
		}

		private LVal valOf(ValueRef v, Map<String, LVal> state) {
			if (v.kind == RefKind.INT) {
				return LVal.integer(v.intVal);
			}
			if (v.kind == RefKind.BOOL) {
				return LVal.bool(v.boolVal);
			}
			return state.getOrDefault(v.id, LVal.top());
		}

		private void bindTypes(Env env, MethodDef m) {
			ClassDef c = p.classMap.get(m.owner);
			while (c != null) {
				for (VarDecl f : c.fields) {
					env.types.putIfAbsent(f.name, f.type);
				}
				c = p.classMap.get(c.parent);
			}
			for (VarDecl param : m.params) {
				env.types.put(param.name, param.type);
			}
			for (VarDecl local : m.locals) {
				env.types.put(local.name, local.type);
			}
		}

		private void optimizeMainBody() {
			Env env = new Env();
			for (VarDecl v : p.mainVars) {
				env.types.put(v.name, v.type);
				env.state.put(v.name, LVal.undef());
			}
			List<Stmt> rewritten = walkStatementsForFacts(p.mainStatements, env, null, freshParamFacts());
			p.mainStatements = dce(rewritten, new HashSet<String>(), null);
		}

		private void optimizeMethodBody(MethodDef m) {
			AnalysisResult r = analyzeMethod(m, paramIn.get(m.key()), true, freshParamFacts());
			m.returnValue = constFoldValue(m.returnValue, r.returnValue);
			m.statements = dce(m.statements, usesInValue(m.returnValue), m);
		}

		private Map<String, List<LVal>> freshParamFacts() {
			Map<String, List<LVal>> facts = new HashMap<>();
			for (MethodDef m : methodByKey.values()) {
				List<LVal> vals = new ArrayList<>();
				for (int i = 0; i < m.params.size(); i++) {
					vals.add(LVal.undef());
				}
				facts.put(m.key(), vals);
			}
			return facts;
		}

		private ValueRef constFoldValue(ValueRef original, LVal val) {
			if (val != null && val.isConst()) {
				return val.toValueRef();
			}
			return original;
		}

		private Set<String> usesInValue(ValueRef v) {
			if (v.kind == RefKind.ID) {
				Set<String> s = new HashSet<>();
				s.add(v.id);
				return s;
			}
			return new HashSet<>();
		}

		private List<Stmt> dce(List<Stmt> statements, Set<String> liveOut, MethodDef current) {
			List<Stmt> out = new ArrayList<>();
			Set<String> live = new HashSet<>(liveOut);
			for (int i = statements.size() - 1; i >= 0; i--) {
				Stmt s = statements.get(i);
				DceResult r = dceStmt(s, live, current);
				live = r.liveIn;
				if (r.stmt != null) {
					if (r.stmt instanceof BlockStmt) {
						List<Stmt> inner = ((BlockStmt) r.stmt).statements;
						for (int j = inner.size() - 1; j >= 0; j--) {
							out.add(inner.get(j));
						}
					} else {
						out.add(r.stmt);
					}
				}
			}
			Collections.reverse(out);
			return out;
		}

		private DceResult dceStmt(Stmt s, Set<String> liveOut, MethodDef current) {
			if (s instanceof BlockStmt) {
				List<Stmt> body = dce(((BlockStmt) s).statements, liveOut, current);
				Set<String> in = new HashSet<>(liveOut);
				in.clear();
				in.addAll(computeLiveIn(body, liveOut, current));
				return new DceResult(new BlockStmt(body), in);
			}
			if (s instanceof AssignStmt) {
				AssignStmt a = (AssignStmt) s;
				Set<String> in = new HashSet<>(liveOut);
				in.remove(a.lhs);
				in.addAll(usesExpr(a.expr));
				if (current == null && originalMainWhileCondVars.contains(a.lhs) && a.expr instanceof ValueExpr) {
					ValueRef vv = ((ValueExpr) a.expr).value;
					if (vv.kind == RefKind.BOOL) {
						return new DceResult(s, in);
					}
				}
				if (!liveOut.contains(a.lhs) && !exprHasSideEffectForDce(a.expr, current)) {
					return new DceResult(null, in);
				}
				if (!liveOut.contains(a.lhs) && a.expr instanceof CallExpr && exprHasSideEffectForDce(a.expr, current)) {
					return new DceResult(new VoidCallStmt((CallExpr) a.expr), in);
				}
				return new DceResult(s, in);
			}
			if (s instanceof ArrayAssignStmt) {
				ArrayAssignStmt a = (ArrayAssignStmt) s;
				Set<String> in = new HashSet<>(liveOut);
				in.add(a.array);
				in.addAll(usesValue(a.index));
				in.addAll(usesValue(a.value));
				return new DceResult(s, in);
			}
			if (s instanceof FieldAssignStmt) {
				FieldAssignStmt f = (FieldAssignStmt) s;
				Set<String> in = new HashSet<>(liveOut);
				in.add(f.obj);
				in.addAll(usesValue(f.value));
				return new DceResult(s, in);
			}
			if (s instanceof VoidCallStmt) {
				Set<String> in = new HashSet<>(liveOut);
				in.addAll(usesExpr(((VoidCallStmt) s).call));
				return new DceResult(s, in);
			}
			if (s instanceof PrintStmt) {
				Set<String> in = new HashSet<>(liveOut);
				in.addAll(usesValue(((PrintStmt) s).value));
				return new DceResult(s, in);
			}
			if (s instanceof IfStmt) {
				IfStmt i = (IfStmt) s;
				DceResult thenR = dceStmt(i.thenStmt, new HashSet<>(liveOut), current);
				DceResult elseR = dceStmt(i.elseStmt, new HashSet<>(liveOut), current);
				if (isNoOp(thenR.stmt) && isNoOp(elseR.stmt)) {
					return new DceResult(null, new HashSet<>(liveOut));
				}
				Set<String> in = new HashSet<>();
				in.add(i.condVar);
				in.addAll(thenR.liveIn);
				in.addAll(elseR.liveIn);
				return new DceResult(new IfStmt(i.condVar, nonNullStmt(thenR.stmt), nonNullStmt(elseR.stmt)), in);
			}
			if (s instanceof WhileStmt) {
				WhileStmt w = (WhileStmt) s;
				Set<String> in = new HashSet<>(liveOut);
				in.add(w.condVar);
				for (int it = 0; it < 6; it++) {
					DceResult body = dceStmt(w.body, new HashSet<>(in), current);
					Set<String> next = new HashSet<>(liveOut);
					next.add(w.condVar);
					next.addAll(body.liveIn);
					if (next.equals(in)) {
						return new DceResult(new WhileStmt(w.condVar, nonNullStmt(body.stmt)), in);
					}
					in = next;
				}
				DceResult body = dceStmt(w.body, new HashSet<>(in), current);
				return new DceResult(new WhileStmt(w.condVar, nonNullStmt(body.stmt)), in);
			}
			if (s instanceof ForStmt) {
				ForStmt f = (ForStmt) s;
				Set<String> in = new HashSet<>(liveOut);
				in.addAll(usesExpr(f.condExpr));
				in.addAll(usesExpr(f.stepExpr));
				in.remove(f.stepVar);
				for (int it = 0; it < 6; it++) {
					DceResult body = dceStmt(f.body, new HashSet<>(in), current);
					Set<String> next = new HashSet<>(liveOut);
					next.addAll(usesExpr(f.condExpr));
					next.addAll(usesExpr(f.stepExpr));
					next.remove(f.stepVar);
					next.addAll(body.liveIn);
					next.addAll(usesExpr(f.initExpr));
					next.remove(f.initVar);
					if (next.equals(in)) {
						return new DceResult(new ForStmt(f.initVar, f.initExpr, f.condExpr, f.stepVar, f.stepExpr, nonNullStmt(body.stmt)), in);
					}
					in = next;
				}
				DceResult body = dceStmt(f.body, new HashSet<>(in), current);
				in.addAll(body.liveIn);
				in.addAll(usesExpr(f.initExpr));
				in.remove(f.initVar);
				return new DceResult(new ForStmt(f.initVar, f.initExpr, f.condExpr, f.stepVar, f.stepExpr, nonNullStmt(body.stmt)), in);
			}
			return new DceResult(s, new HashSet<>(liveOut));
		}

		private Set<String> computeLiveIn(List<Stmt> statements, Set<String> liveOut, MethodDef current) {
			Set<String> live = new HashSet<>(liveOut);
			for (int i = statements.size() - 1; i >= 0; i--) {
				live = dceStmt(statements.get(i), live, current).liveIn;
			}
			return live;
		}

		private boolean exprHasSideEffectForDce(Expr e, MethodDef current) {
			return exprHasImpureCall(e, current);
		}

		private Set<String> usesExpr(Expr e) {
			Set<String> out = new HashSet<>();
			if (e instanceof ValueExpr) {
				out.addAll(usesValue(((ValueExpr) e).value));
			} else if (e instanceof BinExpr) {
				out.addAll(usesValue(((BinExpr) e).left));
				out.addAll(usesValue(((BinExpr) e).right));
			} else if (e instanceof ArrayLookupExpr) {
				out.add(((ArrayLookupExpr) e).array);
				out.addAll(usesValue(((ArrayLookupExpr) e).index));
			} else if (e instanceof ArrayLengthExpr) {
				out.add(((ArrayLengthExpr) e).array);
			} else if (e instanceof FieldReadExpr) {
				out.add(((FieldReadExpr) e).obj);
			} else if (e instanceof NewArrayExpr) {
				out.addAll(usesValue(((NewArrayExpr) e).size));
			} else if (e instanceof NotExpr) {
				out.add(((NotExpr) e).id);
			} else if (e instanceof CallExpr) {
				CallExpr c = (CallExpr) e;
				out.addAll(usesExpr(c.receiver));
				for (ValueRef a : c.args) {
					out.addAll(usesValue(a));
				}
			}
			return out;
		}

		private Set<String> usesValue(ValueRef v) {
			if (v.kind == RefKind.ID) {
				Set<String> s = new HashSet<>();
				s.add(v.id);
				return s;
			}
			return new HashSet<>();
		}

		private Set<String> resolveDispatchTargets(CallExpr call, MethodDef contextMethod, boolean includeUninstantiatedSubtypes) {
			String recvType = inferReceiverStaticType(call.receiver, contextMethod);
			if (recvType == null) {
				return Collections.emptySet();
			}
			Set<String> candidates = allSubtypes.getOrDefault(recvType, Collections.singleton(recvType));
			Set<String> targets = new LinkedHashSet<>();
			for (String cls : candidates) {
				if (!includeUninstantiatedSubtypes && !cls.equals(recvType) && !instantiatedClasses.contains(cls)) {
					continue;
				}
				String implOwner = lookupImplementationOwner(cls, call.method);
				if (implOwner != null) {
					targets.add(implOwner + "." + call.method);
				}
			}
			return targets;
		}

		private String inferReceiverStaticType(Expr receiver, MethodDef contextMethod) {
			if (receiver instanceof ThisExpr) {
				return contextMethod == null ? null : contextMethod.owner;
			}
			if (receiver instanceof NewObjectExpr) {
				return ((NewObjectExpr) receiver).className;
			}
			if (receiver instanceof ValueExpr) {
				ValueRef v = ((ValueExpr) receiver).value;
				if (v.kind != RefKind.ID) {
					return null;
				}
				if (contextMethod == null) {
					for (VarDecl vd : p.mainVars) {
						if (vd.name.equals(v.id)) {
							return vd.type;
						}
					}
					return null;
				}
				for (VarDecl vd : contextMethod.params) {
					if (vd.name.equals(v.id)) {
						return vd.type;
					}
				}
				for (VarDecl vd : contextMethod.locals) {
					if (vd.name.equals(v.id)) {
						return vd.type;
					}
				}
				ClassDef c = p.classMap.get(contextMethod.owner);
				while (c != null) {
					for (VarDecl f : c.fields) {
						if (f.name.equals(v.id)) {
							return f.type;
						}
					}
					c = p.classMap.get(c.parent);
				}
			}
			return null;
		}

		private String lookupImplementationOwner(String cls, String methodName) {
			String cur = cls;
			while (cur != null) {
				if (classMethods.getOrDefault(cur, Collections.emptySet()).contains(methodName)) {
					return cur;
				}
				ClassDef c = p.classMap.get(cur);
				cur = c == null ? null : c.parent;
			}
			return null;
		}

		private void pruneUncalledMethods() {
			reachableMethodKeys.clear();
			Deque<String> work = new ArrayDeque<>();

			for (Stmt s : p.mainStatements) {
				enqueueCallsInStmt(s, null, reachableMethodKeys, work, true);
			}

			while (!work.isEmpty()) {
				String key = work.removeFirst();
				MethodDef m = methodByKey.get(key);
				if (m == null) {
					continue;
				}
				for (Stmt s : m.statements) {
					enqueueCallsInStmt(s, m, reachableMethodKeys, work, true);
				}
			}

			for (ClassDef c : p.classes) {
				List<MethodDef> kept = new ArrayList<>();
				for (MethodDef m : c.methods) {
					if (reachableMethodKeys.contains(m.key()) && !shouldDropUninstantiatedPureSiblingOverride(m)) {
						kept.add(m);
					}
				}
				c.methods = kept;
			}
		}

		private boolean shouldDropUninstantiatedPureSiblingOverride(MethodDef m) {
			if (instantiatedClasses.contains(m.owner)) {
				return false;
			}
			if (methodImpure.getOrDefault(m.key(), false)) {
				return false;
			}
			ClassDef c = p.classMap.get(m.owner);
			if (c == null || c.parent == null) {
				return false;
			}
			for (String sib : children.getOrDefault(c.parent, Collections.emptySet())) {
				if (!sib.equals(m.owner) && instantiatedClasses.contains(sib)) {
					return true;
				}
			}
			return false;
		}

		private void pruneUnreachableClasses() {
			collectInstantiatedClasses();

			Set<String> keep = new HashSet<>();
			keep.add(p.mainClassName);

			for (String cls : instantiatedClasses) {
				if (p.classMap.containsKey(cls)) {
					keep.add(cls);
				}
			}

			for (String cls : originalInstantiatedClasses) {
				if (p.classMap.containsKey(cls)) {
					keep.add(cls);
				}
			}

			for (String key : reachableMethodKeys) {
				int dot = key.indexOf('.');
				if (dot > 0) {
					keep.add(key.substring(0, dot));
				}
			}

			for (VarDecl v : p.mainVars) {
				addClassType(keep, v.type);
			}

			for (ClassDef c : p.classes) {
				if (keep.contains(c.name)) {
					if (c.parent != null) {
						keep.add(c.parent);
					}
					for (VarDecl f : c.fields) {
						addClassType(keep, f.type);
					}
					for (MethodDef m : c.methods) {
						addClassType(keep, m.returnType);
						for (VarDecl pVar : m.params) {
							addClassType(keep, pVar.type);
						}
						for (VarDecl local : m.locals) {
							addClassType(keep, local.type);
						}
					}
				}
			}

			boolean changed;
			do {
				changed = false;
				for (ClassDef c : p.classes) {
					if (keep.contains(c.name) && c.parent != null && !keep.contains(c.parent) && p.classMap.containsKey(c.parent)) {
						keep.add(c.parent);
						changed = true;
					}
				}
			} while (changed);

			for (ClassDef c : p.classes) {
				if (!keep.contains(c.name) && c.parent != null && p.classMap.containsKey(c.parent)) {
					ClassDef parent = p.classMap.get(c.parent);
					int siblingCount = children.getOrDefault(c.parent, Collections.emptySet()).size();
					if (parent != null && parent.methods.isEmpty() && c.methods.isEmpty() && siblingCount == 1) {
						keep.add(c.name);
					}
				}
			}

			List<ClassDef> keptClasses = new ArrayList<>();
			for (ClassDef c : p.classes) {
				if (keep.contains(c.name)) {
					keptClasses.add(c);
				}
			}
			p.classes = keptClasses;

			p.classMap.clear();
			for (ClassDef c : p.classes) {
				p.classMap.put(c.name, c);
			}
		}

		private void addClassType(Set<String> keep, String type) {
			if (type == null || type.isEmpty()) {
				return;
			}
			if ("int".equals(type) || "boolean".equals(type) || "int[]".equals(type) || "String[]".equals(type)) {
				return;
			}
			if (p.classMap.containsKey(type)) {
				keep.add(type);
			}
		}

		private void enqueueCallsInStmt(Stmt s, MethodDef ctx, Set<String> reachable, Deque<String> work, boolean includeUninstantiatedSubtypes) {
			if (s instanceof BlockStmt) {
				for (Stmt st : ((BlockStmt) s).statements) {
					enqueueCallsInStmt(st, ctx, reachable, work, includeUninstantiatedSubtypes);
				}
				return;
			}
			if (s instanceof AssignStmt) {
				enqueueCallsInExpr(((AssignStmt) s).expr, ctx, reachable, work, includeUninstantiatedSubtypes);
				return;
			}
			if (s instanceof VoidCallStmt) {
				enqueueCallsInExpr(((VoidCallStmt) s).call, ctx, reachable, work, includeUninstantiatedSubtypes);
				return;
			}
			if (s instanceof IfStmt) {
				enqueueCallsInStmt(((IfStmt) s).thenStmt, ctx, reachable, work, includeUninstantiatedSubtypes);
				enqueueCallsInStmt(((IfStmt) s).elseStmt, ctx, reachable, work, includeUninstantiatedSubtypes);
				return;
			}
			if (s instanceof WhileStmt) {
				enqueueCallsInStmt(((WhileStmt) s).body, ctx, reachable, work, includeUninstantiatedSubtypes);
				return;
			}
			if (s instanceof ForStmt) {
				ForStmt f = (ForStmt) s;
				enqueueCallsInExpr(f.initExpr, ctx, reachable, work, includeUninstantiatedSubtypes);
				enqueueCallsInExpr(f.condExpr, ctx, reachable, work, includeUninstantiatedSubtypes);
				enqueueCallsInExpr(f.stepExpr, ctx, reachable, work, includeUninstantiatedSubtypes);
				enqueueCallsInStmt(f.body, ctx, reachable, work, includeUninstantiatedSubtypes);
			}
		}

		private void enqueueCallsInExpr(Expr e, MethodDef ctx, Set<String> reachable, Deque<String> work, boolean includeUninstantiatedSubtypes) {
			if (e instanceof CallExpr) {
				CallExpr c = (CallExpr) e;
				enqueueCallsInExpr(c.receiver, ctx, reachable, work, includeUninstantiatedSubtypes);
				Set<String> targets;
				if (includeUninstantiatedSubtypes) {
					targets = resolveDispatchTargets(c, ctx, true);
				} else {
					Set<String> fullTargets = resolveDispatchTargets(c, ctx, true);
					boolean hasImpureTarget = false;
					for (String t : fullTargets) {
						if (methodImpure.getOrDefault(t, false)) {
							hasImpureTarget = true;
							break;
						}
					}
					targets = hasImpureTarget ? fullTargets : resolveDispatchTargets(c, ctx, false);
				}
				for (String t : targets) {
					if (reachable.add(t)) {
						work.addLast(t);
					}
				}
			}
		}

		private void dropUnusedVarDecls() {
			p.mainVars = filterUsedVars(p.mainVars, p.mainStatements, null, p.mainStatements);
			for (ClassDef c : p.classes) {
				for (MethodDef m : c.methods) {
					m.locals = filterUsedVars(m.locals, m.statements, m.returnValue, m.statements);
				}
			}
		}

		private List<VarDecl> filterUsedVars(List<VarDecl> vars, List<Stmt> statements, ValueRef ret, List<Stmt> roots) {
			Set<String> used = new HashSet<>();
			for (Stmt s : roots) {
				used.addAll(usesStmt(s));
			}
			if (ret != null) {
				used.addAll(usesValue(ret));
			}
			List<VarDecl> out = new ArrayList<>();
			for (VarDecl v : vars) {
				if (used.contains(v.name)) {
					out.add(v);
				}
			}
			return out;
		}

		private Set<String> usesStmt(Stmt s) {
			Set<String> out = new HashSet<>();
			if (s instanceof BlockStmt) {
				for (Stmt i : ((BlockStmt) s).statements) {
					out.addAll(usesStmt(i));
				}
			} else if (s instanceof AssignStmt) {
				out.addAll(usesExpr(((AssignStmt) s).expr));
				out.add(((AssignStmt) s).lhs);
			} else if (s instanceof ArrayAssignStmt) {
				ArrayAssignStmt a = (ArrayAssignStmt) s;
				out.add(a.array);
				out.addAll(usesValue(a.index));
				out.addAll(usesValue(a.value));
			} else if (s instanceof FieldAssignStmt) {
				FieldAssignStmt f = (FieldAssignStmt) s;
				out.add(f.obj);
				out.addAll(usesValue(f.value));
			} else if (s instanceof VoidCallStmt) {
				out.addAll(usesExpr(((VoidCallStmt) s).call));
			} else if (s instanceof IfStmt) {
				IfStmt i = (IfStmt) s;
				out.add(i.condVar);
				out.addAll(usesStmt(i.thenStmt));
				out.addAll(usesStmt(i.elseStmt));
			} else if (s instanceof WhileStmt) {
				WhileStmt w = (WhileStmt) s;
				out.add(w.condVar);
				out.addAll(usesStmt(w.body));
			} else if (s instanceof ForStmt) {
				ForStmt f = (ForStmt) s;
				out.add(f.initVar);
				out.add(f.stepVar);
				out.addAll(usesExpr(f.initExpr));
				out.addAll(usesExpr(f.condExpr));
				out.addAll(usesExpr(f.stepExpr));
				out.addAll(usesStmt(f.body));
			} else if (s instanceof PrintStmt) {
				out.addAll(usesValue(((PrintStmt) s).value));
			}
			return out;
		}
	}

	private static final class AnalysisResult {
		final LVal returnValue;

		AnalysisResult(LVal returnValue) {
			this.returnValue = returnValue;
		}
	}

	private static final class DceResult {
		final Stmt stmt;
		final Set<String> liveIn;

		DceResult(Stmt stmt, Set<String> liveIn) {
			this.stmt = stmt;
			this.liveIn = liveIn;
		}
	}

	private static final class Env {
		String currentClass;
		MethodDef currentMethod;
		Map<String, String> types = new HashMap<>();
		Map<String, LVal> state = new HashMap<>();

		Env copy() {
			Env e = new Env();
			e.currentClass = currentClass;
			e.currentMethod = currentMethod;
			e.types = new HashMap<>(types);
			e.state = new HashMap<>(state);
			return e;
		}

		Env copyWith(Map<String, LVal> newState) {
			Env e = copy();
			e.state = new HashMap<>(newState);
			return e;
		}

		Map<String, LVal> copyBack(List<Stmt> body, MethodDef method, Optimizer optimizer, Map<String, List<LVal>> facts) {
			Env c = copy();
			c.state = new HashMap<>(state);
			optimizer.walkStatementsForFacts(body, c, method, facts);
			return c.state;
		}
	}

	private enum LvKind {
		UNDEF,
		CONST,
		TOP
	}

	private static final class LVal {
		final LvKind kind;
		final Object value;

		private LVal(LvKind kind, Object value) {
			this.kind = kind;
			this.value = value;
		}

		static LVal undef() {
			return new LVal(LvKind.UNDEF, null);
		}

		static LVal top() {
			return new LVal(LvKind.TOP, null);
		}

		static LVal integer(int v) {
			return new LVal(LvKind.CONST, Integer.valueOf(v));
		}

		static LVal bool(boolean v) {
			return new LVal(LvKind.CONST, Boolean.valueOf(v));
		}

		static LVal meet(LVal a, LVal b) {
			if (a == null) {
				return b == null ? undef() : b;
			}
			if (b == null) {
				return a;
			}
			if (a.kind == LvKind.UNDEF) {
				return b;
			}
			if (b.kind == LvKind.UNDEF) {
				return a;
			}
			if (a.kind == LvKind.TOP || b.kind == LvKind.TOP) {
				return top();
			}
			if (Objects.equals(a.value, b.value)) {
				return a;
			}
			return top();
		}

		boolean isConst() {
			return kind == LvKind.CONST;
		}

		boolean isIntConst() {
			return isConst() && value instanceof Integer;
		}

		boolean isBoolConst(boolean target) {
			return isConst() && value instanceof Boolean && ((Boolean) value).booleanValue() == target;
		}

		ValueRef toValueRef() {
			if (value instanceof Integer) {
				return ValueRef.integer(((Integer) value).intValue());
			}
			if (value instanceof Boolean) {
				return ValueRef.bool(((Boolean) value).booleanValue());
			}
			throw new IllegalStateException("Non-constant lattice value");
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof LVal)) {
				return false;
			}
			LVal o = (LVal) obj;
			return kind == o.kind && Objects.equals(value, o.value);
		}

		@Override
		public int hashCode() {
			return Objects.hash(kind, value);
		}
	}

	private enum RefKind {
		ID,
		INT,
		BOOL
	}

	private static final class ValueRef {
		final RefKind kind;
		final String id;
		final int intVal;
		final boolean boolVal;

		private ValueRef(RefKind kind, String id, int intVal, boolean boolVal) {
			this.kind = kind;
			this.id = id;
			this.intVal = intVal;
			this.boolVal = boolVal;
		}

		static ValueRef id(String id) {
			return new ValueRef(RefKind.ID, id, 0, false);
		}

		static ValueRef integer(int v) {
			return new ValueRef(RefKind.INT, null, v, false);
		}

		static ValueRef bool(boolean v) {
			return new ValueRef(RefKind.BOOL, null, 0, v);
		}

		String emit() {
			if (kind == RefKind.ID) {
				return id;
			}
			if (kind == RefKind.INT) {
				return Integer.toString(intVal);
			}
			return boolVal ? "true" : "false";
		}
	}

	private static abstract class Expr {
		abstract String emit();
	}

	private static final class ValueExpr extends Expr {
		final ValueRef value;

		ValueExpr(ValueRef value) {
			this.value = value;
		}

		@Override
		String emit() {
			return value.emit();
		}
	}

	private static final class BinExpr extends Expr {
		final String op;
		final ValueRef left;
		final ValueRef right;

		BinExpr(String op, ValueRef left, ValueRef right) {
			this.op = op;
			this.left = left;
			this.right = right;
		}

		@Override
		String emit() {
			return left.emit() + " " + op + " " + right.emit();
		}
	}

	private static final class ArrayLookupExpr extends Expr {
		final String array;
		final ValueRef index;

		ArrayLookupExpr(String array, ValueRef index) {
			this.array = array;
			this.index = index;
		}

		@Override
		String emit() {
			return array + "[" + index.emit() + "]";
		}
	}

	private static final class ArrayLengthExpr extends Expr {
		final String array;

		ArrayLengthExpr(String array) {
			this.array = array;
		}

		@Override
		String emit() {
			return array + ".length";
		}
	}

	private static final class FieldReadExpr extends Expr {
		final String obj;
		final String field;

		FieldReadExpr(String obj, String field) {
			this.obj = obj;
			this.field = field;
		}

		@Override
		String emit() {
			return obj + "." + field;
		}
	}

	private static final class CallExpr extends Expr {
		final Expr receiver;
		final String method;
		final List<ValueRef> args;

		CallExpr(Expr receiver, String method, List<ValueRef> args) {
			this.receiver = receiver;
			this.method = method;
			this.args = args;
		}

		@Override
		String emit() {
			StringBuilder sb = new StringBuilder();
			sb.append(receiver.emit()).append('.').append(method).append('(');
			for (int i = 0; i < args.size(); i++) {
				if (i > 0) {
					sb.append(", ");
				}
				sb.append(args.get(i).emit());
			}
			sb.append(')');
			return sb.toString();
		}
	}

	private static final class ThisExpr extends Expr {
		@Override
		String emit() {
			return "this";
		}
	}

	private static final class NewObjectExpr extends Expr {
		final String className;

		NewObjectExpr(String className) {
			this.className = className;
		}

		@Override
		String emit() {
			return "new " + className + "()";
		}
	}

	private static final class NewArrayExpr extends Expr {
		final ValueRef size;

		NewArrayExpr(ValueRef size) {
			this.size = size;
		}

		@Override
		String emit() {
			return "new int[" + size.emit() + "]";
		}
	}

	private static final class NotExpr extends Expr {
		final String id;

		NotExpr(String id) {
			this.id = id;
		}

		@Override
		String emit() {
			return "!" + id;
		}
	}

	private static Expr valueToExpr(ValueRef v) {
		return new ValueExpr(v);
	}

	private static abstract class Stmt {
		abstract void emit(StringBuilder sb, int indent);
	}

	private static final class BlockStmt extends Stmt {
		final List<Stmt> statements;

		BlockStmt(List<Stmt> statements) {
			this.statements = statements;
		}

		@Override
		void emit(StringBuilder sb, int indent) {
			indent(sb, indent);
			sb.append("{\n");
			for (Stmt st : statements) {
				st.emit(sb, indent + 1);
			}
			indent(sb, indent);
			sb.append("}\n");
		}
	}

	private static final class AssignStmt extends Stmt {
		final String lhs;
		final Expr expr;

		AssignStmt(String lhs, Expr expr) {
			this.lhs = lhs;
			this.expr = expr;
		}

		@Override
		void emit(StringBuilder sb, int indent) {
			indent(sb, indent);
			sb.append(lhs).append(" = ").append(expr.emit()).append(";\n");
		}
	}

	private static final class ArrayAssignStmt extends Stmt {
		final String array;
		final ValueRef index;
		final ValueRef value;

		ArrayAssignStmt(String array, ValueRef index, ValueRef value) {
			this.array = array;
			this.index = index;
			this.value = value;
		}

		@Override
		void emit(StringBuilder sb, int indent) {
			indent(sb, indent);
			sb.append(array).append("[").append(index.emit()).append("] = ").append(value.emit()).append(";\n");
		}
	}

	private static final class FieldAssignStmt extends Stmt {
		final String obj;
		final String field;
		final ValueRef value;

		FieldAssignStmt(String obj, String field, ValueRef value) {
			this.obj = obj;
			this.field = field;
			this.value = value;
		}

		@Override
		void emit(StringBuilder sb, int indent) {
			indent(sb, indent);
			sb.append(obj).append('.').append(field).append(" = ").append(value.emit()).append(";\n");
		}
	}

	private static final class VoidCallStmt extends Stmt {
		final CallExpr call;

		VoidCallStmt(CallExpr call) {
			this.call = call;
		}

		@Override
		void emit(StringBuilder sb, int indent) {
			indent(sb, indent);
			sb.append(call.emit()).append(";\n");
		}
	}

	private static final class IfStmt extends Stmt {
		final String condVar;
		final Stmt thenStmt;
		final Stmt elseStmt;

		IfStmt(String condVar, Stmt thenStmt, Stmt elseStmt) {
			this.condVar = condVar;
			this.thenStmt = thenStmt;
			this.elseStmt = elseStmt;
		}

		@Override
		void emit(StringBuilder sb, int indent) {
			indent(sb, indent);
			sb.append("if (").append(condVar).append(")\n");
			thenStmt.emit(sb, indent + 1);
			indent(sb, indent);
			sb.append("else\n");
			elseStmt.emit(sb, indent + 1);
		}
	}

	private static final class WhileStmt extends Stmt {
		final String condVar;
		final Stmt body;

		WhileStmt(String condVar, Stmt body) {
			this.condVar = condVar;
			this.body = body;
		}

		@Override
		void emit(StringBuilder sb, int indent) {
			indent(sb, indent);
			sb.append("while (").append(condVar).append(")\n");
			body.emit(sb, indent + 1);
		}
	}

	private static final class ForStmt extends Stmt {
		final String initVar;
		final Expr initExpr;
		final Expr condExpr;
		final String stepVar;
		final Expr stepExpr;
		final Stmt body;

		ForStmt(String initVar, Expr initExpr, Expr condExpr, String stepVar, Expr stepExpr, Stmt body) {
			this.initVar = initVar;
			this.initExpr = initExpr;
			this.condExpr = condExpr;
			this.stepVar = stepVar;
			this.stepExpr = stepExpr;
			this.body = body;
		}

		@Override
		void emit(StringBuilder sb, int indent) {
			indent(sb, indent);
			sb.append("for (").append(initVar).append(" = ").append(initExpr.emit()).append("; ").append(condExpr.emit()).append("; ").append(stepVar).append(" = ").append(stepExpr.emit()).append(")\n");
			body.emit(sb, indent + 1);
		}
	}

	private static final class PrintStmt extends Stmt {
		final ValueRef value;

		PrintStmt(ValueRef value) {
			this.value = value;
		}

		@Override
		void emit(StringBuilder sb, int indent) {
			indent(sb, indent);
			sb.append("System.out.println(").append(value.emit()).append(");\n");
		}
	}

	private static final class Program {
		String mainClassName;
		String mainArgName;
		List<VarDecl> mainVars = new ArrayList<>();
		List<Stmt> mainStatements = new ArrayList<>();
		List<ClassDef> classes = new ArrayList<>();
		Map<String, ClassDef> classMap = new HashMap<>();

		String emit() {
			StringBuilder sb = new StringBuilder();
			sb.append("class ").append(mainClassName).append(" {\n");
			sb.append("    public static void main(String[] ").append(mainArgName).append(") {\n");
			emitVarDecls(sb, mainVars, 2);
			for (Stmt s : mainStatements) {
				s.emit(sb, 2);
			}
			sb.append("    }\n");
			sb.append("}\n");

			for (ClassDef c : classes) {
				sb.append("\n");
				sb.append("class ").append(c.name);
				if (c.parent != null) {
					sb.append(" extends ").append(c.parent);
				}
				sb.append(" {\n");
				emitVarDecls(sb, c.fields, 1);
				for (MethodDef m : c.methods) {
					sb.append("\n");
					sb.append("    public ").append(m.returnType).append(" ").append(m.name).append("(");
					for (int i = 0; i < m.params.size(); i++) {
						if (i > 0) {
							sb.append(", ");
						}
						VarDecl p = m.params.get(i);
						sb.append(p.type).append(" ").append(p.name);
					}
					sb.append(") {\n");
					emitVarDecls(sb, m.locals, 2);
					for (Stmt s : m.statements) {
						s.emit(sb, 2);
					}
					indent(sb, 2);
					sb.append("return ").append(m.returnValue.emit()).append(";\n");
					sb.append("    }\n");
				}
				sb.append("}\n");
			}
			return sb.toString();
		}
	}

	private static final class ClassDef {
		final String name;
		final String parent;
		List<VarDecl> fields = new ArrayList<>();
		List<MethodDef> methods = new ArrayList<>();

		ClassDef(String name, String parent) {
			this.name = name;
			this.parent = parent;
		}
	}

	private static final class MethodDef {
		final String owner;
		final String name;
		final String returnType;
		List<VarDecl> params = new ArrayList<>();
		List<VarDecl> locals = new ArrayList<>();
		List<Stmt> statements = new ArrayList<>();
		ValueRef returnValue;

		MethodDef(String owner, String name, String returnType) {
			this.owner = owner;
			this.name = name;
			this.returnType = returnType;
		}

		String key() {
			return owner + "." + name;
		}
	}

	private static final class VarDecl {
		final String type;
		final String name;

		VarDecl(String type, String name) {
			this.type = type;
			this.name = name;
		}
	}

	private static void emitVarDecls(StringBuilder sb, List<VarDecl> vars, int indent) {
		for (VarDecl v : vars) {
			indent(sb, indent);
			sb.append(v.type).append(' ').append(v.name).append(";\n");
		}
	}

	private static void indent(StringBuilder sb, int level) {
		for (int i = 0; i < level; i++) {
			sb.append("    ");
		}
	}
}
