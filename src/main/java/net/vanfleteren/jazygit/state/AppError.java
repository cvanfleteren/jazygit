package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.settings.SettingsProblem;

/**
 * What the user is told went wrong, as data; the view turns it into text.
 */
public sealed interface AppError {

    /**
     * An operation on the repository failed.
     *
     * @param subject what it was done to, e.g. the branch that was pushed, if the operation names one
     */
    record OperationFailed(Operation operation, Option<String> subject, Failure failure) implements AppError {
    }

    /**
     * A commit was asked for, but nothing has changed.
     */
    record NothingToCommit() implements AppError {
    }

    /**
     * Amending was asked for, but nothing has changed to amend the last commit with.
     */
    record NothingToAmend() implements AppError {
    }

    /**
     * A stash was asked for, but nothing has changed.
     */
    record NothingToStash() implements AppError {
    }

    /**
     * The upstream entered for a push is not {@code <remote> <branch>}.
     */
    record InvalidUpstream(String text) implements AppError {
    }

    /**
     * A pull was asked for, but the branch has no upstream to pull from.
     */
    record NoUpstream(Branch.Name branch) implements AppError {
    }

    /**
     * A pull was asked for, but the upstream of the branch was deleted on the remote.
     *
     * @param upstream the short name the upstream had, e.g. {@code origin/feature}
     */
    record UpstreamGone(Branch.Name branch, String upstream) implements AppError {
    }

    /**
     * A pull of a branch that is not checked out was asked for, but its commits not yet pushed keep it from
     * being fast-forwarded.
     */
    record UnpushedCommits(Branch.Name branch) implements AppError {
    }

    /**
     * A pull request was asked for, but the branch is not on its remote yet, or not with all its commits.
     */
    record NotPushed(Branch.Name branch) implements AppError {
    }

    /**
     * Deleting a remote branch was asked for, but it is the default branch of its remote.
     *
     * @param branch the short name of the remote branch, e.g. {@code origin/main}
     */
    record DefaultBranch(String branch) implements AppError {
    }

    /**
     * Opening a branch on GitHub was asked for, but the branch is on no remote, or its upstream is gone.
     */
    record NotOnRemote(Branch.Name branch) implements AppError {
    }

    /**
     * A pull request was asked for before the pull requests were read, so it is unknown whether the branch has
     * one yet.
     */
    record PullRequestsNotRead() implements AppError {
    }

    /**
     * A pull request was asked for, but the pull requests could not be read, so it is unknown whether the branch
     * has one.
     */
    record PullRequestsUnavailable(Failure failure) implements AppError {
    }

    /**
     * The settings could not be read, or not all of them; the defaults are used instead.
     */
    record SettingsNotLoaded(SettingsProblem problem) implements AppError {
    }

    /**
     * A changed setting could not be kept for the next session; it still holds in this one.
     */
    record SettingsNotSaved(Failure failure) implements AppError {
    }

    /**
     * A recent repository was chosen, but could not be opened; it is not offered anymore.
     *
     * @param directory where it was
     */
    record RepositoryNotOpened(String directory, Failure failure) implements AppError {
    }

    /**
     * The gh executable chosen does not run, so it is not used.
     *
     * @param executable the one chosen; none for the one on the PATH
     */
    record GhExecutableUnusable(Option<String> executable, Failure failure) implements AppError {
    }

    /**
     * The account chosen for the repository could not be configured.
     */
    record GitHubAccountNotSet(Failure failure) implements AppError {
    }

    /**
     * The repository could not be shown again with the settings changed; they hold from the next start.
     */
    record SettingsNotApplied(Failure failure) implements AppError {
    }
}
