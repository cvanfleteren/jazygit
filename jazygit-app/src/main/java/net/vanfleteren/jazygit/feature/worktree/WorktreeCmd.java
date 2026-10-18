package net.vanfleteren.jazygit.feature.worktree;

import java.nio.file.Path;
import net.vanfleteren.jazygit.state.GitOperationCmd;

/**
 * A command that adds or removes a worktree.
 */
public interface WorktreeCmd extends GitOperationCmd {

    /**
     * The path {@code location} names: a leading {@code ~} is the home directory, as in a shell.
     */
    static Path path(String location) {
        return location.equals("~") || location.startsWith("~/")
                ? Path.of(System.getProperty("user.home") + location.substring(1))
                : Path.of(location);
    }
}
