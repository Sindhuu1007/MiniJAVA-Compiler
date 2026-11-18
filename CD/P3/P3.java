import java.util.*;
import syntaxtree.*;
import visitor.*;
import java.io.*;

public class P3 {
    public static void main(String[] args) {
        try {
            Node root = new MiniJavaParser(System.in).Goal();
            SymbolTable st = new SymbolTable();
            BuildST builder = new BuildST(st);
            root.accept(builder, null);
            //st.print();
            st.computeOffsetsandTable();
            //st.printOffsets();
            MiniIRGen gen = new MiniIRGen(st);
            root.accept(gen, null);
        } catch (ParseException e) {
            System.out.println("Parse error: " + e.toString());
        }
    }
}