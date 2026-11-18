import syntaxtree.Goal;

public final class P4 {
    public static void main(String[] args) throws Exception {
        FunkyTacoJavaParser parser = new FunkyTacoJavaParser(System.in);
        Goal goal = parser.Goal();

        ProgramModel model = new ProgramModel();
        ProgramModel.Builder builder = new ProgramModel.Builder(model);
        goal.accept(builder);
        model.finalizeModel();

        InliningPrinter printer = new InliningPrinter(model);
        System.out.print(printer.render(goal));
    }
}
