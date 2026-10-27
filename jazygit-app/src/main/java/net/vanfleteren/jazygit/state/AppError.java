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
                .<AppError>map(upstream -> new UpstreamGone(branch.name(), upstream.name()))
                .getOrElse(() -> new NoUpstream(branch.name()));
    }

    /**
     * What was asked for is refused up front, e.g. a commit while nothing has changed, for the reason one message
     * gives. Each feature declares the refusals it makes, as records of its own, and so adds nothing here or to the
     * view but the message.
     */
    non-sealed interface Refusal extends AppError {
        /**
         * The key of the message that says why.
         */
        String key();

        /**
         * What the message names, in its order, as values; the view decides how they are shown.
         */
        default List<Object> args() {
            return List.empty();
        }
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
     * A pull, or a reset onto the upstream, was asked for, but the branch has no upstream.
     */
    record NoUpstream(BranchName branch) implements Refusal {
        @Override
        public String key() {
            return "error.upstream.none";
        }

        @Override
        public List<Object> args() {
            return List.of(branch);
        }
    }

    /**
     * A pull, or a reset onto the upstream, was asked for, but the upstream of the branch was deleted on the
     * remote.
     *
     * @param upstream the short name the upstream had, e.g. {@code origin/feature}
     */
    record UpstreamGone(BranchName branch, String upstream) implements Refusal {
        @Override
        public String key() {
            return "error.upstream.gone";
        }

        @Override
        public List<Object> args() {
            return List.of(branch, upstream);
        }
    }

    /**
     * Comparing a branch with the main branch was asked for, but there is no main branch to compare it with:
     * none is known, or it is gone.
     */
    record NoBaseBranch(BranchName branch) implements Refusal {
        @Override
        public String key() {
            return "error.divergence.noBase";
        }

        @Override
        public List<Object> args() {
            return List.of(branch);
        }
    }

    /**
     * A pull request was asked for before the pull requests were read, so it is unknown whether the branch has
     * one yet.
     */
    record PullRequestsNotRead() implements Refusal {
        @Override
        public String key() {
            return "error.pullRequest.notRead";
        }
    }

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
