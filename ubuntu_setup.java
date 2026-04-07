//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21+

import java.io.*;
import java.util.*;

/**
 * Minimal Ubuntu 24.04 setup script.
 *
 * Usage:
 *   jbang ubuntu_setup.java            # install base packages only
 *   jbang ubuntu_setup.java --with-r   # include R (heavy)
 *   jbang ubuntu_setup.java --with-tex # include TeX Live (very heavy)
 *   jbang ubuntu_setup.java --all      # include everything
 *
 * Note: must be run as root or with sudo.
 */
class ubuntu_setup {

    // --- Package lists by category ---

    static final List<String> NETWORK = List.of(
        "cadaver", "git", "lftp", "net-tools", "wget", "curl", "xvfb"
    );

    static final List<String> EDITORS = List.of(
        "emacs", "vim", "imagemagick", "tree"
    );

    static final List<String> BUILD = List.of(
        "build-essential", "gfortran", "gcc-doc",
        "flex", "bison",
        "automake", "autoconf", "libtool", "autogen", "shtool",
        "libc6-dev-amd64", "libarchive-dev",
        "cmake", "clang"
    );

    static final List<String> PYTHON = List.of(
        "python3", "python3-pip", "python-is-python3",
        // pyenv build dependencies
        "libffi-dev", "libssl-dev", "zlib1g-dev",
        "liblzma-dev", "libbz2-dev", "libreadline-dev",
        "libsqlite3-dev", "libopencv-dev", "tk-dev"
    );

    // Heavy packages — opt-in only
    static final List<String> R = List.of(
        "r-cran-*"
        // r-bioc-* is not in standard Ubuntu repos; install via R's BiocManager instead
    );

    static final List<String> TEX = List.of(
        "texlive-full", "texlive-lang-all"
    );

    // --- Main ---

    public static void main(String[] args) throws Exception {
        var argList = Arrays.asList(args);
        boolean withR   = argList.contains("--with-r")  || argList.contains("--all");
        boolean withTex = argList.contains("--with-tex") || argList.contains("--all");

        checkRoot();

        run("apt-get", "update");

        install("Network tools",  NETWORK);
        install("Editors / misc", EDITORS);
        install("Build tools",    BUILD);
        install("Python",         PYTHON);

        if (withR) {
            install("R (r-cran-*)", R);
        } else {
            info("Skipping R  — pass --with-r or --all to include");
        }

        if (withTex) {
            install("TeX Live", TEX);
        } else {
            info("Skipping TeX — pass --with-tex or --all to include");
        }

        info("Done.");
        info("Not installed by apt (install manually as needed):");
        info("  Java     — use SDKMAN or download from adoptium.net");
        info("  Node.js  — use nvm");
        info("  conda    — use Miniconda installer");
        info("  Jupyter  — use conda (avoid apt to prevent conda conflicts)");
        info("  Docker   — see https://docs.docker.com/engine/install/ubuntu/");
        info("  Apptainer/Singularity — see official install guides");
    }

    // --- Helpers ---

    static void install(String label, List<String> packages) throws Exception {
        info("Installing: " + label);
        List<String> cmd = new ArrayList<>(List.of("apt-get", "install", "-y", "--ignore-missing"));
        cmd.addAll(packages);
        run(cmd);
    }

    static void run(String... cmd) throws Exception {
        run(Arrays.asList(cmd));
    }

    static void run(List<String> cmd) throws Exception {
        System.out.println("$ " + String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        int exit = pb.start().waitFor();
        if (exit != 0) {
            System.err.println("WARNING: command exited with code " + exit + ": " + String.join(" ", cmd));
        }
    }

    static void checkRoot() {
        String user = System.getProperty("user.name");
        if (!"root".equals(user)) {
            System.err.println("ERROR: must be run as root (sudo jbang ubuntu_setup.java)");
            System.exit(1);
        }
    }

    static void info(String msg) {
        System.out.println("[ubuntu_setup] " + msg);
    }
}
