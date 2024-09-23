//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.github.oogasawa:nigsc-k8s:1.0.0

class NigscK8s {
  public static void main(String[] args) throws Exception {
    com.github.oogasawa.nigsc.k8s.App.main(args);
  }
}
