package net.vanfleteren.jazygit.git;

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
        };
    }
}
