///usr/bin/env jbang "$0" "$@" ; exit $?

// JBang shortcut for Utility-sau3.
// Loads ~/works/Utility-sau3/target/Utility-sau3-2.1.0.jar at runtime.
// SAU3 (Sequence Analysis Utilities 3) for bioinformatics sequence processing.
//
// Usage:
//   jbang sau3.java [args...]

import java.io.File;
import java.net.URLClassLoader;

class sau3 {

  static String fatJar = "/works/Utility-sau3/target/Utility-sau3-2.1.0.jar";

  public static void main(String[] args) throws Exception {
    // Get the user's home directory from Java system properties
    String homeDir = System.getProperty("user.home");
    File jarFile = new File(homeDir + fatJar);

    if (!jarFile.exists()) {
      throw new RuntimeException("JAR file not found: " + jarFile.getAbsolutePath());
    }

    // Dynamically add the JAR file to the classpath
    URLClassLoader classLoader = new URLClassLoader(
        new java.net.URL[] { jarFile.toURI().toURL() },
        sau3.class.getClassLoader()
    );

    // Load the class and invoke the main method
    Class<?> appClass = classLoader.loadClass("com.github.oogasawa.utility.sau3.App");
    appClass.getMethod("main", String[].class).invoke(null, (Object) args);
  }
}

