package net.vanfleteren.jazygit.model;

import java.nio.file.Path;

/**
 * Deletes branches through the {@code git} command line tool, so the remote is reached with the
 * user's own configuration and credentials.
 */
public final class GitCliBranch {

    private static final String DEFAULT_REMOTE = "origin";

    private GitCliBranch() {
    }

    /**
     * Runs {@code git branch -D <name>} in {@code workTree}.
     *
     * @throws IllegalStateException if git refuses; the message then is git's own explanation
     */
    public static void deleteLocal(Path workTree, String name) {
        GitCliCheckout.run(workTree, "git", "branch", "--delete", "--force", name);
    }

    /**
     * Runs {@code git push <remote> --delete <name>} in {@code workTree}, where the remote is the
     * one the branch tracks, or {@code origin}.
     *
     * @throws IllegalStateException if git refuses; the message then is git's own explanation
     */
    public static void deleteRemote(Path workTree, String name) {
        GitCliCheckout.run(workTree, "git", "push", remoteOf(workTree, name), "--delete", name);
    }

    private static String remoteOf(Path workTree, String name) {
        try {
            String remote = GitCliCheckout.run(workTree, "git", "config", "--get", "branch." + name + ".remote");
            return remote.isEmpty() ? DEFAULT_REMOTE : remote;
        } catch (IllegalStateException e) {
            // Not configured: git config exits with 1.
            return DEFAULT_REMOTE;
        }
    }
}
