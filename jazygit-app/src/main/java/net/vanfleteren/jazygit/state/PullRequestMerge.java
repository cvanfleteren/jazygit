package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.MergeStatus;
import net.vanfleteren.jazygit.git.model.Branch;

/**
 * A merge of a pull request that GitHub was asked for, which it does in the background (ADR 24).
 *
 * @param number   the number of the pull request
 * @param branch   the branch it merges from
 * @param below    the numbers of the pull requests below it in its stack, which it merges too
 * @param status   how it is doing, as GitHub last said
 * @param asked    when GitHub was asked for it
 * @param checking whether GitHub is being asked how it is doing
 */
public record PullRequestMerge(
        int number, Branch.Name branch, List<Integer> below, MergeStatus status, Instant asked, boolean checking) {

    /**
     * How long a pending merge is asked about before it is left to GitHub.
     */
    public static final Duration GIVEN_UP_AFTER = Duration.ofMinutes(10);

    /**
     * A merge GitHub was just asked for, which said {@code status}.
     */
    public static PullRequestMerge asked(
            int number, Branch.Name branch, List<Integer> below, MergeStatus status, Instant asked) {
        return new PullRequestMerge(number, branch, below, status, asked, false);
    }

    /**
     * Whether it merges pull request {@code pullRequest}: its own, or one below it.
     */
    public boolean merges(int pullRequest) {
        return number == pullRequest || below.contains(pullRequest);
    }

    /**
     * Whether GitHub is still to be asked how it is doing: it is pending, and not being asked already.
     */
    public boolean toCheck() {
        return status instanceof MergeStatus.Pending && !checking;
    }

    /**
     * Whether it was pending for too long at {@code now} to ask about it any longer.
     */
    public boolean givenUp(Instant now) {
        return status instanceof MergeStatus.Pending
                && Duration.between(asked, now).compareTo(GIVEN_UP_AFTER) > 0;
    }

    /**
     * This merge, no longer telling how it went for {@code numbers}, which a later merge merges.
     */
    public PullRequestMerge withoutBelow(List<Integer> numbers) {
        return new PullRequestMerge(number, branch, below.removeAll(numbers), status, asked, checking);
    }

    /**
     * This merge while GitHub is asked how it is doing.
     */
    public PullRequestMerge beingChecked() {
        return new PullRequestMerge(number, branch, below, status, asked, true);
    }

    /**
     * This merge as GitHub says it is doing now.
     */
    public PullRequestMerge checked(MergeStatus now) {
        return new PullRequestMerge(number, branch, below, now, asked, false);
    }
}
