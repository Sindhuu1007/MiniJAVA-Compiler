class Main {
    public static void main(String[] args) {
        F f;
        int r;
        f = new F();
        /* INLINE */ r = f.fact(5);
        System.out.println(r);
    }
}

class F {
    public int fact(int n) {
        int t;
        int m;
        boolean b;
        b = n < 2;
        if (b)
            t = 1;
        else {
            m = n - 1;
            t = this.fact(m);
            t = t * n;
        }
        return t;
    }
}
