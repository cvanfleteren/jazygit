package net.vanfleteren.jazygit.feature.github.checks;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.PendingChecks;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Showing the checks on the tip of a branch one by one, with how long they took or run (ADR 32).
 *
 * @param branch        the branch the user asked for the checks of, local or remote
 * @param onGitHub      the branch on GitHub whose checks those are: the one a local branch follows
 * @param checks        what was read of them, or why that failed; nothing while they are first read
 * @param refreshing    whether they are read again, while {@code checks} stay shown
 * @param refreshFailed why reading them again failed the last time, so the checks shown are stale; nothing once a
 *                      read succeeds
 */
public record ChecksPopup(
        BranchRef branch,
        RemoteBranch onGitHub,
        Option<Either<Failure, PendingChecks>> checks,
        boolean refreshing,
        Option<Failure> refreshFailed)
        implements Popup {

    /**
     * The popup while the checks are first read.
     */
    public static ChecksPopup loading(BranchRef branch, RemoteBranch onGitHub) {
        return new ChecksPopup(branch, onGitHub, Option.none(), false, Option.none());
    }

    /**
     * The popup showing {@code read}: the checks, or why reading them failed. Checks that were read already stay
     * shown when reading them again fails, marked stale.
     */
    public ChecksPopup with(Either<Failure, PendingChecks> read) {
        return read.isLeft() && wasRead()
                ? new ChecksPopup(branch, onGitHub, checks, false, Option.of(read.getLeft()))
                : new ChecksPopup(branch, onGitHub, Option.of(read), false, Option.none());
    }

    /**
     * The popup while its checks are read again.
     */
    public ChecksPopup readingAgain() {
        return new ChecksPopup(branch, onGitHub, checks, true, refreshFailed);
    }

    /**
     * Whether the checks can be read again: once they were read, or that failed, and no other read runs.
     */
    public boolean refreshable() {
        return checks.isDefined() && !refreshing;
    }

    /**
     * The checks read, in no particular order; none while they are first read, or when that failed.
     */
    public List<Check> checksRead() {
        return checks.flatMap(Either::toOption)
                .map(read -> read.of(onGitHub.branch()))
                .getOrElse(List.empty());
    }

    private boolean wasRead() {
        return checks.exists(Either::isRight);
    }
}
