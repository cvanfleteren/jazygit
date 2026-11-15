package net.vanfleteren.jazygit.branches.worktree;

import java.nio.file.Path;
import net.vanfleteren.jazygit.state.operation.GitOperationCmd;

/**
 * A command that adds or removes a worktree.
 */
public interface WorktreeCmd extends GitOperationCmd {

    /**
     * The path {@code location} names: a leading {@code ~} is the home directory, as in a shell.
     */
    static Path path(String location) {
        return Path.of(expandedHome(location, System.getProperty("user.home")));
    }

    /**
     * {@code location} with a leading {@code ~} as {@code home}, as a shell takes it: alone or before a {@code /}.
     */
    static String expandedHome(String location, String home) {
        return location.equals("~") || location.startsWith("~/") ? home + location.substring(1) : location;
    }
}
