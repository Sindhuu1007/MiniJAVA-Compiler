class Main {
    public static void main (String[] args) {
        A a;
        int r;
        A inline1_self;
        int inline1_n;
        int inline1_t;
        
        a = new A();
        inline1_self = a;
        inline1_n = 3;
        inline1_t = inline1_n + 1;
        r = inline1_t;
        System.out.println (r);
    }
}
class A{
    public int add(int n) {
        int t;
        t = n + 1;
        return t;
    }
}
