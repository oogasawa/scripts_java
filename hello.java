///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.github.lalyos:jfiglet:0.0.8

// JBang demo script using jfiglet.
// Prints "Hello InfoQ" in ASCII art.
//
// Usage:
//   jbang hello.java

import com.github.lalyos.jfiglet.FigletFont;

class hello {
  public static void main(String... args) throws Exception {
    System.out.println(FigletFont.convertOneLine("Hello InfoQ"));
  }
}
