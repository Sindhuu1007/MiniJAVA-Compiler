class Main {
  public static void main(String[] args) {
    A a;
    int r;
    A inline1_self;
    int inline1_n;
    int inline1_r;
    B inline2_st_helper;
    B inline3_ld_helper;
    a = new A();
  
      inline1_self = a;
      inline1_n = 7;
      inline2_st_helper = new B();
      inline1_self.helper = inline2_st_helper;
      inline3_ld_helper = inline1_self.helper;
      /* INLINE */ inline1_r = inline3_ld_helper.work(inline1_n); //???
      r = inline1_r;
  
    System.out.println(r);
  }
}
class A {
  B helper;
  public int entry(int n) {
    int r;
    int inline4_n;
    int inline4_t;
    helper = new B();
  
      inline4_n = n;
      inline4_t = inline4_n * 3;
      inline4_t = inline4_t + 1;
      r = inline4_t;

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
