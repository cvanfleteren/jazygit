package net.vanfleteren.jazygit.ui;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Failure;

/**
 * Describes errors to the user. The only place that turns them into text.
 */
public final class ErrorText {

    private ErrorText() {
    }

    public static String of(AppError error) {
        return switch (error) {
            case AppError.OperationFailed(var operation, Option<String> subject, Failure failure) ->
                    Messages.get(operation.errorKey(), subject.toList().<Object>map(s -> s).append(of(failure)).toJavaArray());
            case AppError.NothingToCommit() -> Messages.get("error.commit.nothing");
            case AppError.NothingToAmend() -> Messages.get("error.amend.nothing");
            case AppError.NothingToStash() -> Messages.get("error.stash.nothing");
            case AppError.InvalidUpstream(String text) -> Messages.get("error.upstream.invalid", text.strip());
            case AppError.NoUpstream(String branch) -> Messages.get("error.pull.noUpstream", branch);
            case AppError.UnpushedCommits(String branch) -> Messages.get("error.fastForward.unpushed", branch);
        };
    }

    public static String of(Failure failure) {
        return switch (failure) {
            case Failure.Git(GitError error, var commands, var cleanupFailed) -> cleanupFailed
                    .map(cleanup -> Messages.get("error.cleanupFailed", of(error), of(cleanup)))
                    .getOrElse(() -> of(error));
            case Failure.Bug(Throwable cause) -> Messages.get("error.bug", reason(cause));
        };
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
