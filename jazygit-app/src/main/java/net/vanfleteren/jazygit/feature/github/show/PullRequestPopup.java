package net.vanfleteren.jazygit.feature.github.show;

import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Failure;

/**
 * Showing the pull request of {@code branch}.
 *
 * @param pullRequest what is known of it from the list of pull requests
 * @param details     what was read about it, or why that failed; nothing while it is being read
 */
public record PullRequestPopup(
        Branch.Name branch, PullRequest pullRequest, Option<Either<Failure, PullRequestDetails>> details)
        implements ShowsPullRequest {

    /**
     * The popup while its details are being read.
     */
    public static PullRequestPopup loading(Branch.Name branch, PullRequest pullRequest) {
        return new PullRequestPopup(branch, pullRequest, Option.none());
    }

    public PullRequestPopup with(Either<Failure, PullRequestDetails> read) {
        return new PullRequestPopup(branch, pullRequest, Option.of(read));
    }

    /**
     * The popup once the pull request became a draft when {@code draft}, else ready for review.
     */
    PullRequestPopup withDraft(boolean draft) {
        return new PullRequestPopup(branch, pullRequest, details.map(read -> read.map(pr -> pr.withDraft(draft))));
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
