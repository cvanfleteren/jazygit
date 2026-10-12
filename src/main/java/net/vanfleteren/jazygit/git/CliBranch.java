package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;

import java.nio.file.Path;

import static net.vanfleteren.jazygit.git.GitCommand.git;

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
    public static List<String> deleteLocal(Path workTree, String name) {
        return GitProcess.runOrThrow(workTree, git("branch", "--delete", "--force", name)).log();
    }

    /**
     * Runs {@code git push <remote> --delete <name>} in {@code workTree}, where the remote is the
     * one the branch tracks, or {@code origin}.
     *
     * @throws IllegalStateException if git refuses; the message then is git's own explanation
     */
    public static List<String> deleteRemote(Path workTree, String name) {
        return GitProcess.runOrThrow(workTree, git("push", remoteOf(workTree, name), "--delete", name)).log();
    }

    /**
     * Runs {@code git push <remote> <name>} in {@code workTree}, where the remote is the one the
     * branch tracks, or {@code origin}. A branch without an upstream gets one, as with
     * {@code --set-upstream}.
     *
     * @param forceWithLease also overwrite a remote branch that has diverged, unless somebody
     *                       pushed to it since the last fetch
     * @throws IllegalStateException if git refuses; the message then is git's own explanation
     */
    public static List<String> push(Path workTree, String name, boolean forceWithLease) {
        String remote = remoteOf(workTree, name);
        if (hasUpstream(workTree, name)) {
            return GitProcess.runOrThrow(workTree, forceWithLease
                    ? git("push", "--force-with-lease", remote, name)
                    : git("push", remote, name)).log();
        }
        return GitProcess.runOrThrow(workTree, git("push", "--set-upstream", remote, name)).log();
    }

    private static boolean hasUpstream(Path workTree, String name) {
        try {
            return !GitProcess.runOrThrow(workTree, git("config", "--get", "branch." + name + ".merge")).stdout().isBlank();
        } catch (IllegalStateException e) {
            // Not configured: git config exits with 1.
            return false;
        }
    }

    private static String remoteOf(Path workTree, String name) {
        try {
            String remote = GitProcess.runOrThrow(workTree, git("config", "--get", "branch." + name + ".remote")).stdout().strip();
            return remote.isEmpty() ? DEFAULT_REMOTE : remote;
        } catch (IllegalStateException e) {
            // Not configured: git config exits with 1.
            return DEFAULT_REMOTE;
        }
    }
}
