package net.vanfleteren.jazygit.feature.github.show;

import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.gh.timing.CheckTimings;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Reading;

/**
 * Showing the pull request of {@code branch}.
 *
 * @param pullRequest   what is known of it from the list of pull requests
 * @param details       what was read about it, or why that failed; nothing while it is first being read
 * @param reading       whether it is being read again, while {@code details} stay shown, and why
 * @param refreshFailed why reading it again failed the last time, so the details shown are stale; nothing once a
 *                      read succeeds
 * @param readAt        when the last read of it started
 * @param read          the id of the last read of it that it started, which it waits for while it reads (ADR 34)
 */
public record PullRequestPopup(
        BranchName branch,
        PullRequest pullRequest,
        Option<Either<Failure, PullRequestDetails>> details,
        Reading reading,
        Option<Failure> refreshFailed,
        Instant readAt,
        long read)
        implements ShowsPullRequest {

    /**
     * The popup while its details are being read, from {@code now}, by read {@code read}.
     */
    public static PullRequestPopup loading(BranchName branch, PullRequest pullRequest, Instant now, long read) {
        return new PullRequestPopup(branch, pullRequest, Option.none(), Reading.NOT, Option.none(), now, read);
    }

    /**
     * The popup showing {@code result}, of read {@code id}: the details, or why reading them failed. Details that
     * were read already stay shown when reading them again fails, marked stale. How far the base moved on is read
     * apart from the rest and can fail alone: the count shown stays when the pull request is still open at the same
     * commit on the same base, where the base can only have moved on further. It still reads when {@code id} is not
     * the read it waits for.
     */
    public PullRequestPopup with(Either<Failure, PullRequestDetails> result, long id) {
        Reading after = id == read ? Reading.NOT : reading;
        return result.isLeft() && detailsRead()
                ? new PullRequestPopup(branch, pullRequest, details, after, Option.of(result.getLeft()), readAt, read)
                : new PullRequestPopup(
                        branch,
                        pullRequest,
                        Option.of(result.map(this::keepingBehindBase)),
                        after,
                        Option.none(),
                        readAt,
                        read);
    }

    /**
     * The popup once read {@code id} came back too late to be taken (ADR 34): what it shows stays, and when that is
     * the read it waits for, it no longer reads.
     */
    PullRequestPopup notTaken(long id) {
        return id == read
                ? new PullRequestPopup(branch, pullRequest, details, Reading.NOT, refreshFailed, readAt, read)
                : this;
    }

    private PullRequestDetails keepingBehindBase(PullRequestDetails read) {
        return read.behindBase().isDefined()
                        || read.state() != PullRequestDetails.State.OPEN
                        || read.head().isEmpty()
                ? read
                : readDetails()
                        .filter(shown -> shown.head().equals(read.head())
                                && shown.baseBranch().equals(read.baseBranch()))
                        .flatMap(PullRequestDetails::behindBase)
                        .map(read::withBehindBase)
                        .getOrElse(read);
    }

    /**
     * The popup while its details are read again from {@code now} by read {@code id}, as {@code reading} tells.
     */
    PullRequestPopup readingAgain(Reading reading, Instant now, long id) {
        return new PullRequestPopup(branch, pullRequest, details, reading, refreshFailed, now, id);
    }

    /**
     * The popup showing the read it does by itself as one asked for.
     */
    PullRequestPopup asked() {
        return new PullRequestPopup(branch, pullRequest, details, Reading.ASKED, refreshFailed, readAt, read);
    }

    /**
     * Whether its details are read again, while they stay shown.
     */
    public boolean refreshing() {
        return reading != Reading.NOT;
    }

    /**
     * Whether its details can be read again: once the first read is done, and no other is running.
     */
    public boolean refreshable() {
        return details.isDefined() && !refreshing();
    }

    /**
     * Whether its details are due to be read again by themselves at {@code now}: one of its checks is not done, as far
     * as the last read that did not fail tells, or the last read started {@link CheckTimings#READ_WHEN_DONE} ago or
     * more. After a read failed, the details shown are stale, so they are read every minute, as ADR 19 has a failing
     * read do (ADR 34).
     */
    public boolean dueByItself(Instant now) {
        return refreshFailed.isEmpty()
                        && readDetails().exists(read -> read.checks().exists(check -> !check.done()))
                || !now.isBefore(readAt.plus(CheckTimings.READ_WHEN_DONE));
    }

    /**
     * The popup once the pull request became a draft when {@code draft}, else ready for review.
     */
    PullRequestPopup withDraft(boolean draft) {
        return new PullRequestPopup(
                branch,
                pullRequest,
                details.map(read -> read.map(pr -> pr.withDraft(draft))),
                reading,
                refreshFailed,
                readAt,
                read);
    }

    /**
     * Whether it is a draft, once its details are read.
     */
    public Option<Boolean> draft() {
        return readDetails().map(PullRequestDetails::draft);
    }

    /**
     * Whether its details were read, rather than being read still or having failed to.
     */
    public boolean detailsRead() {
        return readDetails().isDefined();
    }

    /**
     * Whether it is known to be open, so it can be closed: once its details are read.
     */
    public boolean open() {
        return readDetails().exists(details -> details.state() == PullRequestDetails.State.OPEN);
    }

    /**
     * Whether it can be merged as far as is known: it is open, not a draft, and does not conflict. GitHub works
     * out whether it conflicts lazily, so an unknown answer does not stop it. Failing checks, missing reviews
     * and branch protection are left to gh, whose refusal is shown as the error of the merge.
     */
    public boolean mergeable() {
        return open()
                && readDetails()
                        .exists(details ->
                                !details.draft() && details.mergeable() != PullRequestDetails.Mergeable.CONFLICTING);
    }

    /**
     * The commit its branch was at when it was read, which a merge is only done at; empty before it is read.
     */
    public String head() {
        return readDetails().map(PullRequestDetails::head).getOrElse("");
    }

    /**
     * Where it sits in its stack, if it is stacked: as it was read, else as it is listed.
     */
    public Option<PullRequestStack> stack() {
        return readDetails().map(PullRequestDetails::stack).getOrElse(pullRequest::stack);
    }

    @Override
    public PullRequestPopup pullRequestPopup() {
        return this;
    }

    @Override
    public PullRequestPopup withPullRequestPopup(PullRequestPopup dialog) {
        return dialog;
    }

    private Option<PullRequestDetails> readDetails() {
        return details.flatMap(Either::toOption);
    }
}
