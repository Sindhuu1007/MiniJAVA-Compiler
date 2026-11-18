import syntaxtree.Goal;

public class P1 {
	public static void main(String[] args) throws Exception {
		Goal goal = new BuritoJavaParser(System.in).Goal();

		SymbolTableBuilder builder = new SymbolTableBuilder();
		ProgramInfo program = builder.build(goal);

		try {
			AnalysisVisitor analyzer = new AnalysisVisitor(program);
			analyzer.visit(goal, null);
			System.out.println("No issue with variables.");
		} catch (IssueException ex) {
			System.out.println(ex.getMessage());
		}
	}
}