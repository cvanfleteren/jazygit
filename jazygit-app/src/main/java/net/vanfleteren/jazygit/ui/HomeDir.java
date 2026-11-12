package net.vanfleteren.jazygit.ui;

import java.io.File;

/**
 * Paths as they are shown: from {@code ~} when they are in the home directory.
 */
public final class HomeDir {

    private static final String HOME = System.getProperty("user.home");

    private HomeDir() {}

    /**
     * {@code path}, starting with {@code ~} instead of the home directory when it is in it; as it is otherwise.
     */
    public static String shown(String path) {
        return path.equals(HOME) || path.startsWith(HOME + File.separator) ? "~" + path.substring(HOME.length()) : path;
    }
}
