///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.github.oogasawa:autonomous-mirror:1.1.0

// JBang shortcut for autonomous-mirror.
// Mirrors Git repositories autonomously.
//
// Project: com.github.oogasawa:autonomous-mirror
//
// Usage:
//   jbang autonomous-mirror.java [args...]

class sau3 {
  public static void main(String[] args) throws Exception {
    com.github.oogasawa.mirror.App.main(args);
  }
}
