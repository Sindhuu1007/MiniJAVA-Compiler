class T2 {
    public static void main(String[] a) {
        System.out.println(new T().foo());
    }
}

class T {
    public int foo() {
        int x;
        int y;
        for (y = 1; true; y = y+1) {
            x = y;
        }

        return y;
    }
}