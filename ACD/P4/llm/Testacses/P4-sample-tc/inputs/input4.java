class Main {
    public static void main(String[] args) {
        A a;
        int r;
        a = new A();
        /* INLINE */ r = a.entry(7);
        System.out.println(r);
    }
}

class A {
    B helper;
    public int entry(int n) {
        int r;
        helper = new B();
        /* INLINE */ r = helper.work(n);
        return r;
    }
}
class B {
    public int work(int n) {
        int t;
        t = n * 3;
        t = t + 1;
        return t;
    }
}
