import java.util.*;
import syntaxtree.*;
import visitor.*;
import java.io.*;

public class P4 {
    public static void main(String[] args) {
        try {
            Node root = new MiniIRParser(System.in).Goal();
            Conversion conv = new Conversion();
            root.accept(conv, null);
            conv.printMicroIR();
        } catch (ParseException e) {
            System.out.println("Parse error: " + e.toString());
        }
    }
}