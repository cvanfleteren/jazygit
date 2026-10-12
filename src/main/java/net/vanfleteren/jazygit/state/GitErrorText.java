package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.i18n.Messages;

/**
 * Describes a {@link GitError} to the user.
 */
public final class GitErrorText {

    private GitErrorText() {
    }

    public static String of(GitError error) {
        return switch (error) {
            case GitError.GitUnavailable() -> Messages.get("git.notInstalled");
            case GitError.TimedOut(String operation, var workTree) -> Messages.get("git.timedOut", operation, workTree);
            case GitError.Interrupted(String operation) -> Messages.get("git.interrupted", operation);
            case GitError.CommandFailed(String explanation, int exitCode) -> explanation.isEmpty()
                    ? Messages.get("git.exitedWithCode", String.valueOf(exitCode))
                    : explanation;
            case GitError.NotARepository(var directory) -> Messages.get("git.notRepository", directory);
            case GitError.OpenFailed(var gitDir, String reason) -> Messages.get("git.openFailed", gitDir, reason);
            case GitError.NoWorkTree(var gitDir) -> Messages.get("git.noWorkTree", gitDir);
            case GitError.RepositoryUnreadable(String reason) -> Messages.get("git.repositoryUnreadable", reason);
            case GitError.UnknownBranch(String branch) -> Messages.get("git.unknownBranch", branch);
            case GitError.LocalChanges(String explanation) -> explanation;
            case GitError.StashNotRestored(var cause) -> Messages.get("git.stashPopFailed", of(cause));
            case GitError.TempFileFailed(String reason) -> Messages.get("git.tempFileFailed", reason);
            case GitError.RefusedPath(String path) -> Messages.get("git.refusedPath", path);
            case GitError.DeleteFailed(String path, String reason) -> Messages.get("git.deleteFailed", path, reason);
        };
    }
}
