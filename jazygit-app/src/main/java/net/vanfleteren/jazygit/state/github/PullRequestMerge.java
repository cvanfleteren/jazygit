package net.vanfleteren.jazygit.state.github;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Traversable;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.GhCommit;
import net.vanfleteren.jazygit.gh.model.MergeStatus;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.git.model.BranchName;

/**
 * A merge of a pull request that GitHub was asked for, which it does in the background (ADR 24).
 *
 * @param number      the number of the pull request
 * @param branch      the branch it merges from
 * @param below       the numbers of the pull requests below it in its stack, which it merges too
 * @param status      how it is doing, as GitHub last said
 * @param asked       when GitHub was asked for it
 * @param checking    whether GitHub is being asked how it is doing
 * @param failedWhile once it failed, how the pull requests it merges stood then; none before (ADR 119)
 */
public record PullRequestMerge(
        PrNumber number,
        BranchName branch,
        List<PrNumber> below,
        MergeStatus status,
        Instant asked,
        boolean checking,
        Option<FailedWhile> failedWhile) {

    /**
     * How long a pending merge is asked about before it is left to GitHub.
     */
    public static final Duration GIVEN_UP_AFTER = Duration.ofMinutes(10);

    /**
     * A merge GitHub was just asked for, which said {@code status}.
     */
    public static PullRequestMerge asked(
            PrNumber number, BranchName branch, List<PrNumber> below, MergeStatus status, Instant asked) {
        return new PullRequestMerge(number, branch, below, status, asked, false, Option.none());
    }

    /**
     * Whether it merges pull request {@code pullRequest}: its own, or one below it.
     */
    public boolean merges(PrNumber pullRequest) {
        return number.equals(pullRequest) || below.contains(pullRequest);
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
     * Whether it failed, and read {@code read}, which started after that, lists the {@code open} pull requests without
     * one it merges standing as it did then: changed, or no longer listed. Merging it again may go now, so the failure
     * no longer shows (ADR 119).
     */
    public boolean outdatedBy(Traversable<PullRequest> open, long read) {
        return status instanceof MergeStatus.Failed && failedWhile.exists(failed -> failed.outdatedBy(open, read));
    }

    /**
     * This merge, once it failed while the pull requests it merges stood as the {@code open} ones listed, with
     * {@code read} the id the next read gets. Unchanged when it did not fail, or was told so before.
     */
    public PullRequestMerge failedAmong(Traversable<PullRequest> open, long read) {
        return status instanceof MergeStatus.Failed && failedWhile.isEmpty()
                ? withFailedWhile(Option.of(new FailedWhile(
                        read, open.filter(pr -> merges(pr.number())).toMap(PullRequest::number, Standing::of))))
                : this;
    }

    /**
     * This merge, no longer telling how it went for {@code numbers}, which a later merge merges.
     */
    public PullRequestMerge withoutBelow(List<PrNumber> numbers) {
        return new PullRequestMerge(
                number,
                branch,
                below.removeAll(numbers),
                status,
                asked,
                checking,
                failedWhile.map(failed -> failed.without(numbers)));
    }

    /**
     * This merge while GitHub is asked how it is doing.
     */
    public PullRequestMerge beingChecked() {
        return new PullRequestMerge(number, branch, below, status, asked, true, failedWhile);
    }

    /**
     * This merge as GitHub says it is doing now.
     */
    public PullRequestMerge checked(MergeStatus now) {
        return new PullRequestMerge(number, branch, below, now, asked, false, failedWhile);
    }

    private PullRequestMerge withFailedWhile(Option<FailedWhile> failed) {
        return new PullRequestMerge(number, branch, below, status, asked, checking, failed);
    }

    /**
     * How the pull requests a merge merges stood when it failed (ADR 119).
     *
     * @param read     the id the next read got then: only a read with this id or a later one started after it failed
     * @param standing how each of them stood, by its number, as far as they were listed
     */
    public record FailedWhile(long read, Map<PrNumber, Standing> standing) {

        /**
         * Whether read {@code read} started after it failed, and lists the {@code open} pull requests without one of
         * them standing as it did then.
         */
        public boolean outdatedBy(Traversable<PullRequest> open, long read) {
            return read >= this.read
                    && standing.exists(stood -> !open.find(pr -> pr.number().equals(stood._1))
                            .map(Standing::of)
                            .contains(stood._2));
        }

        /**
         * This, no longer about pull requests {@code numbers}.
         */
        public FailedWhile without(List<PrNumber> numbers) {
            return new FailedWhile(read, standing.removeAll(numbers));
        }
    }

    /**
     * What of a pull request may change what merging it does: once one of them changed, a merge that failed before
     * may no longer fail (ADR 119).
     *
     * @param checks how its checks are doing
     * @param review where its review stands
     * @param draft  whether it is a draft
     * @param head   the commit its branch is at, when GitHub said
     */
    public record Standing(Checks checks, Review review, boolean draft, Option<GhCommit> head) {

        /**
         * How {@code pullRequest} stands.
         */
        public static Standing of(PullRequest pullRequest) {
            return new Standing(
                    pullRequest.checks(),
                    pullRequest.review(),
                    pullRequest.draft(),
                    pullRequest.commits().map(PullRequest.Commits::head));
        }
    }
}
