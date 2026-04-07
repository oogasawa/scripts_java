//usr/bin/env jbang "$0" "$@" ; exit $?

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class fcitx5_ime_osd {

    private static final String JAR_RELATIVE_PATH =
        "/.m2/repository/com/github/oogasawa/fcitx5-ime-osd/1.0.0/fcitx5-ime-osd-1.0.0.jar";

    public static void main(String[] args) throws Exception {
        String homeDir = System.getProperty("user.home");
        File jarFile = new File(homeDir + JAR_RELATIVE_PATH);

        if (!jarFile.exists()) {
            throw new IllegalStateException("JAR file not found: " + jarFile.getAbsolutePath());
        }

        // Build command to run jar in a separate process
        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-jar");
        command.add(jarFile.getAbsolutePath());

        // Add any additional arguments
        for (String arg : args) {
            command.add(arg);
        }

        // Start the process
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO(); // Inherit stdin/stdout/stderr
        Process process = pb.start();

        // Wait for the process to complete
        int exitCode = process.waitFor();
        System.exit(exitCode);
    }
}
