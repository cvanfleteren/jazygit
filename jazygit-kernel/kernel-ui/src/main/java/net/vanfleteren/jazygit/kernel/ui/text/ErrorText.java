package net.vanfleteren.jazygit.kernel.ui.text;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.kernel.settings.SettingsProblem;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.state.preferences.SettingsError;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;

/**
 * Describes errors to the user. The only place that turns them into text.
 */
public final class ErrorText {

    private ErrorText() {}

    public static String of(AppError error) {
        return of(error.phrase());
    }

    /**
     * {@code phrase} in the user's language.
     */
    public static String of(Phrase phrase) {
        return switch (phrase) {
            case Phrase.Keyed(String key, var args) ->
                Messages.get(key, args.map(ErrorText::argument).toJavaArray());
            case Phrase.Verbatim(String text) -> text;
            case Phrase.Count(int count) -> String.valueOf(count);
            case Phrase.Failed(Failure failure) -> of(failure);
            case Phrase.Joined(String separator, var parts) ->
                parts.map(ErrorText::of).mkString(separator);
        };
    }

    public static String of(Failure failure) {
        return switch (failure) {
            case Failure.Git(GitError error, var commands, var cleanupFailed) ->
                cleanupFailed
                        .map(cleanup -> Messages.get("error.cleanupFailed", of(error), of(cleanup)))
                        .getOrElse(() -> of(error));
            case Failure.Phrased phrased -> of(phrased.phrase());
            case Failure.Settings(SettingsProblem problem) -> of(problem);
            case Failure.Bug(Throwable cause) -> Messages.get("error.bug", reason(cause));
        };
    }

    public static String of(SettingsProblem problem) {
        return of(SettingsError.said(problem));
    }

    public static String of(GitError error) {
        return switch (error) {
            case GitError.GitUnavailable(var cause) -> Messages.get("git.notInstalled");
            case GitError.TimedOut(String operation, var worktree) -> Messages.get("git.timedOut", operation, worktree);
            case GitError.Interrupted(String operation) -> Messages.get("git.interrupted", operation);
            case GitError.TooMuchOutput(String operation) -> Messages.get("git.tooMuchOutput", operation);
            case GitError.Broke(String operation, Throwable cause) ->
                Messages.get("git.broke", operation, reason(cause));
            case GitError.CommandFailed(var explanation, int exitCode, var output) ->
                explanation.getOrElse(() -> Messages.get("git.exitedWithCode", String.valueOf(exitCode)));
            case GitError.UnexpectedOutput(String output) -> Messages.get("git.unexpectedOutput", output);
            case GitError.NotARepository(var directory) -> Messages.get("git.notRepository", directory);
            case GitError.NoWorktree(var directory) -> Messages.get("git.noWorkTree", directory);
            case GitError.Unreadable(String path, String reason) -> Messages.get("git.unreadable", path, reason);
            case GitError.UnknownBranch(BranchRef branch) -> Messages.get("git.unknownBranch", branch.name());
            case GitError.LocalChanges(String explanation) -> explanation;
            case GitError.WorktreeHasChanges(String explanation) -> explanation;
            case GitError.NotFullyMerged(String explanation) -> explanation;
            case GitError.StashNotRestored(var cause) -> Messages.get("git.stashPopFailed", of(cause));
            case GitError.StashGone(String message) -> Messages.get("git.stashGone", message);
            case GitError.StackBranchMoved(BranchName branch) -> Messages.get("git.stackBranchMoved", branch.value());
            case GitError.StackBranchCheckedOutElsewhere(BranchName branch, String worktree) ->
                Messages.get("git.stackBranchCheckedOutElsewhere", branch.value(), worktree);
            case GitError.PushRefused(String pushDefault, RemoteBranch upstream) ->
                Messages.get("git.pushRefused", pushDefault, upstream.shortName());
            case GitError.UpstreamNotRenamed(RemoteBranch upstream, BranchName newName, var cause) ->
                Messages.get(
                        "git.upstreamNotRenamed",
                        upstream.shortName(),
                        newName.value(),
                        of(cause),
                        upstream.remote().value(),
                        upstream.branch().value());
            case GitError.SshKeyRefused(String explanation) -> Messages.get("git.sshKeyRefused", explanation);
            case GitError.SshCancelled(String prompt) -> Messages.get("git.sshCancelled", prompt);
            case GitError.UpstreamNotOnRemote(RemoteBranch upstream, BranchName newName) ->
                Messages.get("git.upstreamNotOnRemote", upstream.shortName(), newName.value());
            case GitError.StoppedOnConflicts(var inProgress, int conflicts) ->
                Messages.get("git.stoppedOnConflicts", conflicts);
            case GitError.TempFileFailed(Throwable cause) -> Messages.get("git.tempFileFailed", reason(cause));
            case GitError.RefusedPath(String path) -> Messages.get("git.refusedPath", path);
            case GitError.EditorNotStarted(String editor, var cause) ->
                Messages.get("git.editorNotStarted", editor, reason(cause));
            case GitError.EditorFailed(String editor, int exitCode) ->
                Messages.get("git.editorFailed", editor, String.valueOf(exitCode));
            case GitError.ProgramNotStarted(String program, var cause) ->
                Messages.get("git.programNotStarted", program, reason(cause));
            case GitError.ProgramFailed(String program, int exitCode) ->
                Messages.get("git.programFailed", program, String.valueOf(exitCode));
        };
    }

    /**
     * What an exception says about why it happened, or what kind it is when it says nothing.
     */
    public static String reason(Throwable cause) {
        return Option.of(cause.getMessage())
                .filter(message -> !message.isBlank())
                .getOrElse(() -> cause.getClass().getSimpleName());
    }

    /**
     * What goes into a message for {@code phrase}: a number stays one, for the message to format.
     */
    private static Object argument(Phrase phrase) {
        return phrase instanceof Phrase.Count(int count) ? count : of(phrase);
    }
}
