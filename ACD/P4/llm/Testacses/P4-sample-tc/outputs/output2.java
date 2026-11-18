class Main {
  public static void main(String[] args) {
    F f;
    int r;
    F inline1_self;
    int inline1_n;
    int inline1_t;
    int inline1_m;
    boolean inline1_b;
    f = new F();

      inline1_self = f;
      inline1_n = 5;
      inline1_b = inline1_n < 2;
      if (inline1_b)
        inline1_t = 1;
      else
        {
          inline1_m = inline1_n - 1;
          inline1_t = inline1_self.fact(inline1_m);
          inline1_t = inline1_t * inline1_n;
        }
      r = inline1_t;

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
    else
      {
        m = n - 1;
        t = this.fact(m);
        t = t * n;
      }
    return t;
  }
}
