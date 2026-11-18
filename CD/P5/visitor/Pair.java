
package visitor;

public class Pair<U, V> {
   public final U f;
   public final V s;
   public Pair(U first, V second) {
      this.f = first;
      this.s = second;
   }
   public U first(){
      return this.f;
   }
   public V second() {
      return this.s;
   }
}