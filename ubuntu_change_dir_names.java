//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21+

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Replaces Japanese XDG user directories with English equivalents on Ubuntu.
 *
 * Equivalent to running:
 *   LANG=C xdg-user-dirs-update --force
 * followed by removing the Japanese-named directories left behind.
 *
 * Usage:
 *   jbang ubuntu_change_dir_names.java
 *
 * Note: Run once after a fresh Ubuntu install when the system locale was
 * Japanese at first boot, leaving ~/デスクトップ, ~/ダウンロード, etc.
 */
class ubuntu_change_dir_names {

    static final List<String> JAPANESE_DIRS = List.of(
        "テンプレート",
        "デスクトップ",
        "ビデオ",
        "ミュージック",
        "ダウンロード",
        "ドキュメント",
        "公開",
        "ピクチャ"
    );

    public static void main(String[] args) throws Exception {
        String home = System.getProperty("user.home");

        // Regenerate XDG user dirs in English
        run("env", "LANG=C", "xdg-user-dirs-update", "--force");

        // Remove leftover Japanese directories
        for (String dir : JAPANESE_DIRS) {
            Path path = Path.of(home, dir);
            if (Files.exists(path)) {
                deleteRecursively(path);
                System.out.println("Removed: " + path);
            }
        }

        System.out.println("Done.");
    }

    static void run(String... cmd) throws Exception {
        System.out.println("$ " + String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        int exit = pb.start().waitFor();
        if (exit != 0) {
            System.err.println("WARNING: command exited with code " + exit);
        }
    }

    static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                for (Path child : stream.toList()) {
                    deleteRecursively(child);
                }
            }
        }
        Files.delete(path);
    }
}
