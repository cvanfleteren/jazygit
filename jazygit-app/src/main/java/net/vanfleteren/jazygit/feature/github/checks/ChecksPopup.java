package net.vanfleteren.jazygit.feature.github.checks;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.timing.CheckTimings;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.PendingChecks;
import net.vanfleteren.jazygit.state.Popup;
import net.vanfleteren.jazygit.state.Reading;

/**
 * Showing the checks on the tip of a branch one by one, with how long they took or run (ADR 32), read again every so
 * often (ADR 34).
 *
 * @param branch        the branch the user asked for the checks of, local or remote
 * @param onGitHub      the branch on GitHub whose checks those are: the one a local branch follows
 * @param checks        what was read of them, or why that failed; nothing while they are first read
 * @param reading       whether they are read again, while {@code checks} stay shown, and why
 * @param refreshFailed why reading them again failed the last time, so the checks shown are stale; nothing once a
 *                      read succeeds
 * @param readAt        when the last read of them started
 */
public record ChecksPopup(
        BranchRef branch,
        RemoteBranch onGitHub,
        Option<Either<Failure, PendingChecks>> checks,
        Reading reading,
        Option<Failure> refreshFailed,
        Instant readAt)
        implements Popup {

    /**
     * The popup while the checks are first read, from {@code now}.
     */
    public static ChecksPopup loading(BranchRef branch, RemoteBranch onGitHub, Instant now) {
        return new ChecksPopup(branch, onGitHub, Option.none(), Reading.NOT, Option.none(), now);
    }

    /**
     * The popup showing {@code read}: the checks, or why reading them failed. Checks that were read already stay
     * shown when reading them again fails, marked stale.
     */
    public ChecksPopup with(Either<Failure, PendingChecks> read) {
        return read.isLeft() && wasRead()
                ? new ChecksPopup(branch, onGitHub, checks, Reading.NOT, Option.of(read.getLeft()), readAt)
                : new ChecksPopup(branch, onGitHub, Option.of(read), Reading.NOT, Option.none(), readAt);
    }

    /**
     * The popup while its checks are read again from {@code now}, as {@code reading} tells.
     */
    public ChecksPopup readingAgain(Reading reading, Instant now) {
        return new ChecksPopup(branch, onGitHub, checks, reading, refreshFailed, now);
    }

    /**
     * Whether the checks are read again, while they stay shown.
     */
    public boolean refreshing() {
        return reading != Reading.NOT;
    }

    /**
     * Whether the checks can be read again: once they were read, or that failed, and no other read runs.
     */
    public boolean refreshable() {
        return checks.isDefined() && !refreshing();
    }

    /**
     * Whether the checks are due to be read again by themselves at {@code now}: one of them is not done, or the last
     * read started {@link CheckTimings#READ_WHEN_DONE} ago or more (ADR 34).
     */
    public boolean dueByItself(Instant now) {
        return checksRead().exists(check -> !check.done()) || !now.isBefore(readAt.plus(CheckTimings.READ_WHEN_DONE));
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
