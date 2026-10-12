package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
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
            case GitError.GitUnavailable(var cause) -> Messages.get("git.notInstalled");
            case GitError.TimedOut(String operation, var workTree) -> Messages.get("git.timedOut", operation, workTree);
            case GitError.Interrupted(String operation) -> Messages.get("git.interrupted", operation);
            case GitError.CommandFailed(var explanation, int exitCode) ->
                    explanation.getOrElse(() -> Messages.get("git.exitedWithCode", String.valueOf(exitCode)));
            case GitError.UnexpectedOutput(String output) -> Messages.get("git.unexpectedOutput", output);
            case GitError.NotARepository(var directory) -> Messages.get("git.notRepository", directory);
            case GitError.NoWorkTree(var directory) -> Messages.get("git.noWorkTree", directory);
            case GitError.UnknownBranch(String branch) -> Messages.get("git.unknownBranch", branch);
            case GitError.LocalChanges(String explanation) -> explanation;
            case GitError.StashNotRestored(var cause) -> Messages.get("git.stashPopFailed", of(cause));
            case GitError.TempFileFailed(Throwable cause) -> Messages.get("git.tempFileFailed", reason(cause));
            case GitError.RefusedPath(String path) -> Messages.get("git.refusedPath", path);
            case GitError.DeleteFailed(String path, Throwable cause) -> Messages.get("git.deleteFailed", path, reason(cause));
        };
    }

    /**
     * What an exception says about why it happened, or what kind it is when it says nothing.
     */
    private static String reason(Throwable cause) {
        return Option.of(cause.getMessage()).filter(message -> !message.isBlank())
                .getOrElse(() -> cause.getClass().getSimpleName());
    }
}
