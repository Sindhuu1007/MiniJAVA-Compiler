class Main {
  public static void main(String[] args) {
    Shape s;
    int r1;
    int r2;
    int inline1_r;
    int inline1_t;
    int inline2_s;
    int inline2_t;
    s = new Circle();

      inline1_r = 5;
      inline1_t = inline1_r * inline1_r;
      inline1_t = inline1_t * 3;
      r1 = inline1_t;
      
    s = new Square();

      inline2_s = 4;
      inline2_t = inline2_s * inline2_s;
      r2 = inline2_t;
    
    System.out.println(r1);
    System.out.println(r2);
  }
}
class Shape {
  public int area(int n) {
    return n;
  }
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
