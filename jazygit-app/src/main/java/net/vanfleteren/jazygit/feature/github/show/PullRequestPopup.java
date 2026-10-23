package net.vanfleteren.jazygit.feature.github.show;

import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Failure;

/**
 * Showing the pull request of {@code branch}.
 *
 * @param pullRequest   what is known of it from the list of pull requests
 * @param details       what was read about it, or why that failed; nothing while it is first being read
 * @param refreshing    whether it is being read again, while {@code details} stay shown
 * @param refreshFailed why reading it again failed the last time, so the details shown are stale; nothing once a
 *                      read succeeds
 */
public record PullRequestPopup(
        Branch.Name branch,
        PullRequest pullRequest,
        Option<Either<Failure, PullRequestDetails>> details,
        boolean refreshing,
        Option<Failure> refreshFailed)
        implements ShowsPullRequest {

    /**
     * The popup while its details are being read.
     */
    public static PullRequestPopup loading(Branch.Name branch, PullRequest pullRequest) {
        return new PullRequestPopup(branch, pullRequest, Option.none(), false, Option.none());
    }

    /**
     * The popup showing {@code read}: the details, or why reading them failed. Details that were read already
     * stay shown when reading them again fails, marked stale. How far the base moved on is read apart from the rest
     * and can fail alone: the count shown stays when the pull request is still open at the same commit on the same
     * base, where the base can only have moved on further.
     */
    public PullRequestPopup with(Either<Failure, PullRequestDetails> read) {
        return read.isLeft() && detailsRead()
                ? new PullRequestPopup(branch, pullRequest, details, false, Option.of(read.getLeft()))
                : new PullRequestPopup(
                        branch, pullRequest, Option.of(read.map(this::keepingBehindBase)), false, Option.none());
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
     * The popup while its details are read again.
     */
    PullRequestPopup readingAgain() {
        return new PullRequestPopup(branch, pullRequest, details, true, refreshFailed);
    }

    /**
     * Whether its details can be read again: once the first read is done, and no other is running.
     */
    public boolean refreshable() {
        return details.isDefined() && !refreshing;
    }

    /**
     * The popup once the pull request became a draft when {@code draft}, else ready for review.
     */
    PullRequestPopup withDraft(boolean draft) {
        return new PullRequestPopup(
                branch,
                pullRequest,
                details.map(read -> read.map(pr -> pr.withDraft(draft))),
                refreshing,
                refreshFailed);
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
