class Main {
    public static void main(String[] args) {
        Acc a;
        int i;
        int sum;
        boolean b;
        a = new Acc();
        sum = 0;
        i = 0;
        b = i < 5;
        while (b) {
            /* INLINE */ sum = a.add(sum, i);
            i = i + 1;
            b = i < 5;
        }
        System.out.println(sum);
    }
}

class Acc {
    public int add(int x, int y) {
        int t;
        t = x + y;
        return t;
    }
}
