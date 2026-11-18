class Main {
  public static void main(String[] args) {
    Acc a;
    int i;
    int sum;
    boolean b;
    int inline1_x;
    int inline1_y;
    int inline1_t;
    a = new Acc();
    sum = 0;
    i = 0;
    b = i < 5;
    while (b)
      {

          inline1_x = sum;
          inline1_y = i;
          inline1_t = inline1_x + inline1_y;
          sum = inline1_t;
      
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
