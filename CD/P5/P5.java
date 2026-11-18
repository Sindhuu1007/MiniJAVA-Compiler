import java.util.*;
import syntaxtree.*;
import visitor.*;
import java.io.*;

public class P5 {
    public static void main(String[] args) {
        try {
            Map<String, ProcInfo> procTable = new HashMap<>();
            Node root = new microIRParser(System.in).Goal();
            Storing s = new Storing(procTable);
            root.accept(s);
            //s.print();
            Succ succ = new Succ(s.procTable);
            root.accept(succ);
            //succ.print();
            InOut inout = new InOut(s.procTable);
            inout.computeInOut();
            //inout.print();
            LiveRanges lr = new LiveRanges(s.procTable);
            lr.computeLiveRanges();
            //lr.print();
            Merger merger = new Merger(s.procTable);
            merger.mergeIntervals();
            //merger.print();
            LSA lsa = new LSA(s.procTable);
            Map<String, LSA.Allocation> allocations = lsa.allocate();
            //lsa.print();
            Printing p = new Printing(s.procTable, allocations);
            root.accept(p);
            
        } catch (ParseException e) {
            System.out.println("Parse error: " + e.toString());
        }
    }
    //in out kanukkodaaniki function
    //oka function raayaali... Live ranges calculate cheyadaaniki
    //live ranges ni calculate cheyadaaniki
    //3rd parse emo printing ki
}//