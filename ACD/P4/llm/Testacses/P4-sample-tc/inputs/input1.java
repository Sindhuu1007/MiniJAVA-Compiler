class Main {
    public static void main(String[] args) {
        A a;
        int r;
        a = new A();
        /* INLINE */ r = a.add(3);
        System.out.println(r);
    }
}

class A {
    public int add(int n) {
        int t;
        t = n + 1;
        return t;
    }
}
