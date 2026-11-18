class Main {
    public static void main(String[] args) {
        Shape s;
        int r1;
        int r2;
        s = new Circle();
        /* INLINE */ r1 = s.area(5);
        s = new Square();
        /* INLINE */ r2 = s.area(4);
        System.out.println(r1);
        System.out.println(r2);
    }
}

class Shape {
    public int area(int n) { return n; }
}
class Circle extends Shape {
    public int area(int r) {
        int t;
        t = r * r;
        t = t * 3;
        return t;
    }
}
class Square extends Shape {
    public int area(int s) {
        int t;
        t = s * s;
        return t;
    }
}
