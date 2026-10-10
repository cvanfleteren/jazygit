package net.vanfleteren.jazygit.git;

import java.nio.file.Path;

/**
 * Deletes branches through the {@code git} command line tool, so the remote is reached with the
 * user's own configuration and credentials.
 */
public final class CliBranch {

    private static final String DEFAULT_REMOTE = "origin";

    private CliBranch() {
    }

    /**
     * Runs {@code git branch -D <name>} in {@code workTree}.
     *
     * @throws IllegalStateException if git refuses; the message then is git's own explanation
     */
    public static void deleteLocal(Path workTree, String name) {
        CliCheckout.run(workTree, "git", "branch", "--delete", "--force", name);
    }

    /**
     * Runs {@code git push <remote> --delete <name>} in {@code workTree}, where the remote is the
     * one the branch tracks, or {@code origin}.
     *
     * @throws IllegalStateException if git refuses; the message then is git's own explanation
     */
    public static void deleteRemote(Path workTree, String name) {
        CliCheckout.run(workTree, "git", "push", remoteOf(workTree, name), "--delete", name);
    }

    /**
     * Runs {@code git push <remote> <name>} in {@code workTree}, where the remote is the one the
     * branch tracks, or {@code origin}. A branch without an upstream gets one, as with
     * {@code --set-upstream}.
     *
     * @throws IllegalStateException if git refuses; the message then is git's own explanation
     */
    public static void push(Path workTree, String name) {
        String remote = remoteOf(workTree, name);
        if (hasUpstream(workTree, name)) {
            CliCheckout.run(workTree, "git", "push", remote, name);
        } else {
            CliCheckout.run(workTree, "git", "push", "--set-upstream", remote, name);
        }
    }

    private static boolean hasUpstream(Path workTree, String name) {
        try {
            return !CliCheckout.run(workTree, "git", "config", "--get", "branch." + name + ".merge").isEmpty();
        } catch (IllegalStateException e) {
            // Not configured: git config exits with 1.
            return false;
        }
    }

    private static String remoteOf(Path workTree, String name) {
        try {
            String remote = CliCheckout.run(workTree, "git", "config", "--get", "branch." + name + ".remote");
            return remote.isEmpty() ? DEFAULT_REMOTE : remote;
        } catch (IllegalStateException e) {
            // Not configured: git config exits with 1.
            return DEFAULT_REMOTE;
        }
    }
}
