package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.settings.SettingsProblem;

/**
 * What the user is told went wrong, as data; the view turns it into text.
 */
public sealed interface AppError {
    /**
     * Why {@code branch} has no upstream: none was set, or it was deleted on the remote.
     */
    static AppError noUpstream(Branch branch) {
        return branch.goneUpstream()
                .map(upstream -> refused("error.upstream.gone", branch.name().value(), upstream.name()))
                .getOrElse(() -> refused("error.upstream.none", branch.name().value()));
    }

    /**
     * What was asked for is refused, for the reason the message {@code key} gives with {@code args}.
     */
    static AppError refused(String key, Object... args) {
        return new Refused(key, List.of(args));
    }

    /**
     * An operation on the repository failed.
     *
     * @param subject what it was done to, e.g. the branch that was pushed, if the operation names one
     */
    record OperationFailed(Operation operation, Option<String> subject, Failure failure) implements AppError {}

    /**
     * GitHub did not merge the pull request of {@code branch}, which it was asked to do in the background.
     *
     * @param message why, as GitHub says it
     */
    record MergeFailed(BranchName branch, String message) implements AppError {}

    /**
     * What was asked for is refused up front, for the reason the message {@code key} gives with {@code args}, e.g.
     * that nothing has changed to commit. The feature that refuses names the message, so a refusal that says no
     * more than one message needs no record of its own.
     *
     * @param args what the message names, as they are shown
     */
    record Refused(String key, List<Object> args) implements AppError {}

    /**
     * An operation was asked for that waits until the repository is in the middle of nothing, see
     * {@link Operation#waitsForUnfinished()}, but it is in the middle of {@code inProgress}, or {@code conflicts}
     * files still have conflicts.
     *
     * @param subject what it was to be done to, as for {@link OperationFailed}
     */
    record Unfinished(Operation operation, Option<String> subject, Option<InProgress> inProgress, int conflicts)
            implements AppError {}

    /**
     * An operation was asked for on a branch that is checked out in another worktree, which keeps git from
     * doing it here.
     *
     * @param worktree the root of the worktree the branch is checked out in
     */
    record CheckedOutElsewhere(Operation operation, BranchName branch, String worktree) implements AppError {}

    /**
     * A locked worktree was asked to be removed, which its lock keeps git from doing. Unlike the other reasons a
     * worktree is not removed, this one goes away once it is unlocked.
     *
     * @param worktree the name of its directory
     * @param reason   why it was locked; empty when no reason was given
     */
    record WorktreeLocked(String worktree, String reason) implements AppError {}

    /**
     * A pull request was asked for, but the pull requests could not be read, so it is unknown whether the branch
     * has one.
     */
    record PullRequestsUnavailable(Failure failure) implements AppError {}

    /**
     * The settings could not be read, or not all of them; the defaults are used instead.
     */
    record SettingsNotLoaded(SettingsProblem problem) implements AppError {}

    /**
     * A changed setting could not be kept for the next session; it still holds in this one.
     */
    record SettingsNotSaved(Failure failure) implements AppError {}

    /**
     * A recent repository was chosen, but could not be opened; it is not offered anymore.
     *
     * @param directory where it was
     */
    record RepositoryNotOpened(String directory, Failure failure) implements AppError {}

    /**
     * The gh executable chosen does not run, so it is not used.
     *
     * @param executable the one chosen; none for the one on the PATH
     */
    record GhExecutableUnusable(Option<String> executable, Failure failure) implements AppError {}

    /**
     * The account chosen for the repository could not be configured.
     */
    record GitHubAccountNotSet(Failure failure) implements AppError {}

    /**
     * The repository could not be shown again with the settings changed; they hold from the next start.
     */
    record SettingsNotApplied(Failure failure) implements AppError {}
}
