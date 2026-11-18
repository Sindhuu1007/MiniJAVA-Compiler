import java.util.*;
import syntaxtree.*;
import visitor.*;
import java.io.*;

public class P6 {
    public static void main(String[] args) {
        try {
            Node root = new MiniRAParser(System.in).Goal();
            Conversion c = new Conversion();
            root.accept(c);
            
        } catch (ParseException e) {
            System.out.println("Parse error: " + e.toString());
        }
    }
}