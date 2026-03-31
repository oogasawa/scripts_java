///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.github.oogasawa:nigsc-k8s:1.0.0

// JBang shortcut for nigsc-k8s.
// NIG Supercomputer Kubernetes management utilities.
//
// Usage:
//   jbang nigsc-k8s.java [args...]

class NigscK8s {
  public static void main(String[] args) throws Exception {
    com.github.oogasawa.nigsc.k8s.App.main(args);
  }
}
