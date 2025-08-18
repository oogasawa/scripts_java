//usr/bin/env jbang "$0" "$@" ; exit $?

import java.io.File;
import java.net.URLClassLoader;

class sc {

  static String fatJar = "/works/Utility-sc/target/Utility-sc-2.2.0-fat.jar";

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
        sc.class.getClassLoader()
    );

    // Load the class and invoke the main method
    Class<?> appClass = classLoader.loadClass("com.github.oogasawa.utility.sc.App");
    appClass.getMethod("main", String[].class).invoke(null, (Object) args);
  }
}

