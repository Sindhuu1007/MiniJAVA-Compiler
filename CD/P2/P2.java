import java.util.*;
import syntaxtree.*;
import visitor.*;


public class P2 {
    public static void main(String[] args) {
        try {
            // Parse input program
            Node root = new MiniJavaParser(System.in).Goal();
            //System.out.println("Program parsed successfully");

            // Create SymbolTable
            SymbolTable st = new SymbolTable();

            // Run your BuildST visitor
            BuildST builder = new BuildST(st);
            root.accept(builder, null);

            // Print SymbolTable contents
            //st.print();

            st.checkOverriding();

            TypeChecking tc = new TypeChecking(st);
            root.accept(tc, null);

            System.out.println("Program type checked successfully");
        } catch (ParseException e) {
            System.out.println("Parse error: " + e.toString());
        }
    }
}
