package net.vanfleteren.jazygit.ui;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.GhError;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.settings.SettingsProblem;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Failure;

/**
 * Describes errors to the user. The only place that turns them into text.
 */
public final class ErrorText {

    private ErrorText() {}

    public static String of(AppError error) {
        return switch (error) {
            case AppError.OperationFailed(var operation, Option<String> subject, Failure failure) ->
                Messages.get(
                        operation.errorKey(),
                        subject.toList().<Object>map(s -> s).append(of(failure)).toJavaArray());
            case AppError.MergeFailed(BranchName branch, String message) ->
                Messages.get("error.mergePr.failed", branch.value(), message);
            case AppError.Refusal refusal ->
                Messages.get(refusal.key(), refusal.args().map(ErrorText::shown).toJavaArray());
            case AppError.Unfinished(var operation, Option<String> subject, var inProgress, int conflicts) ->
                Messages.get(
                        operation.errorKey(),
                        subject.toList()
                                .<Object>map(s -> s)
                                .append(inProgress
                                        .map(op -> Messages.get("error.inProgress", InProgressText.kind(op)))
                                        .getOrElse(() -> Messages.get("error.conflictsRemain", conflicts)))
                                .toJavaArray());
            case AppError.CheckedOutElsewhere(var operation, BranchName branch, var worktree) ->
                Messages.get(operation.errorKey(), branch.value(), Messages.get("error.checkedOutElsewhere", worktree));
            case AppError.PullRequestsUnavailable(Failure failure) ->
                Messages.get("error.pullRequest.unavailable", of(failure));
            case AppError.SettingsNotLoaded(SettingsProblem problem) -> of(problem);
            case AppError.SettingsNotSaved(Failure failure) -> Messages.get("error.settings.notSaved", of(failure));
            case AppError.RepositoryNotOpened(String directory, Failure failure) ->
                Messages.get("error.recentRepos.notOpened", directory, of(failure));
            case AppError.GhExecutableUnusable(Option<String> executable, Failure failure) ->
                Messages.get("error.settings.ghUnusable", executable.getOrElse("gh"), of(failure));
            case AppError.GitHubAccountNotSet(Failure failure) ->
                Messages.get("error.settings.accountNotSet", of(failure));
            case AppError.SettingsNotApplied(Failure failure) -> Messages.get("error.settings.notApplied", of(failure));
        };
    }

    public static String of(Failure failure) {
        return switch (failure) {
            case Failure.Git(GitError error, var commands, var cleanupFailed) ->
                cleanupFailed
                        .map(cleanup -> Messages.get("error.cleanupFailed", of(error), of(cleanup)))
                        .getOrElse(() -> of(error));
            case Failure.GitHub(GhError error, var commands, var cleanupFailed) ->
                cleanupFailed
                        .map(cleanup -> Messages.get("error.cleanupFailed", of(error), of(cleanup)))
                        .getOrElse(() -> of(error));
            case Failure.Settings(SettingsProblem problem) -> of(problem);
            case Failure.Bug(Throwable cause) -> Messages.get("error.bug", reason(cause));
        };
    }

    public static String of(SettingsProblem problem) {
        return switch (problem) {
            case SettingsProblem.Unreadable(var file, String reason) ->
                Messages.get("settings.unreadable", file, reason);
            case SettingsProblem.Invalid(var file, var keys) ->
                Messages.get("settings.invalid", file, keys.mkString(", "));
            case SettingsProblem.Unwritable(var file, String reason) ->
                Messages.get("settings.unwritable", file, reason);
        };
    }

    public static String of(GhError error) {
        return switch (error) {
            case GhError.GhUnavailable(var cause) -> Messages.get("gh.notInstalled");
            case GhError.TimedOut(String operation) -> Messages.get("gh.timedOut", operation);
            case GhError.Interrupted(String operation) -> Messages.get("gh.interrupted", operation);
            case GhError.CommandFailed(var explanation, int exitCode) ->
                explanation.getOrElse(() -> Messages.get("gh.exitedWithCode", String.valueOf(exitCode)));
            case GhError.UnexpectedOutput(String output) -> Messages.get("gh.unexpectedOutput", output);
            case GhError.TokenUnavailable(var account, GhError cause) ->
                Messages.get("gh.tokenUnavailable", account.user(), account.host(), of(cause));
        };
    }

    public static String of(GitError error) {
        return switch (error) {
            case GitError.GitUnavailable(var cause) -> Messages.get("git.notInstalled");
            case GitError.TimedOut(String operation, var worktree) -> Messages.get("git.timedOut", operation, worktree);
            case GitError.Interrupted(String operation) -> Messages.get("git.interrupted", operation);
            case GitError.CommandFailed(var explanation, int exitCode, var output) ->
                explanation.getOrElse(() -> Messages.get("git.exitedWithCode", String.valueOf(exitCode)));
            case GitError.UnexpectedOutput(String output) -> Messages.get("git.unexpectedOutput", output);
            case GitError.NotARepository(var directory) -> Messages.get("git.notRepository", directory);
            case GitError.NoWorktree(var directory) -> Messages.get("git.noWorkTree", directory);
            case GitError.UnknownBranch(BranchRef branch) -> Messages.get("git.unknownBranch", branch.name());
            case GitError.LocalChanges(String explanation) -> explanation;
            case GitError.WorktreeHasChanges(String explanation) -> explanation;
            case GitError.NotFullyMerged(String explanation) -> explanation;
            case GitError.StashNotRestored(var cause) -> Messages.get("git.stashPopFailed", of(cause));
            case GitError.StashGone(String message) -> Messages.get("git.stashGone", message);
            case GitError.StoppedOnConflicts(var inProgress, int conflicts) ->
                Messages.get("git.stoppedOnConflicts", conflicts);
            case GitError.TempFileFailed(Throwable cause) -> Messages.get("git.tempFileFailed", reason(cause));
            case GitError.RefusedPath(String path) -> Messages.get("git.refusedPath", path);
            case GitError.EditorNotStarted(String editor, var cause) ->
                Messages.get("git.editorNotStarted", editor, reason(cause));
            case GitError.EditorFailed(String editor, int exitCode) ->
                Messages.get("git.editorFailed", editor, String.valueOf(exitCode));
        };
    }

    /**
     * How a value a refusal names is shown in its message: a commit by its short sha, a branch by its name.
     */
    static Object shown(Object value) {
        return switch (value) {
            case BranchName branch -> branch.value();
            case RemoteBranch branch -> branch.shortName();
            case Sha sha -> sha.shortSha();
            default -> value;
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
}
