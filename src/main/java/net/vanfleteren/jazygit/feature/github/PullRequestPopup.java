package net.vanfleteren.jazygit.feature.github;

import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Showing the pull request of {@code branch}.
 *
 * @param pullRequest what is known of it from the list of pull requests
 * @param details     what was read about it, or why that failed; nothing while it is being read
 */
public record PullRequestPopup(String branch, PullRequest pullRequest,
                               Option<Either<Failure, PullRequestDetails>> details) implements Popup {

    /**
     * The popup while its details are being read.
     */
    static PullRequestPopup loading(String branch, PullRequest pullRequest) {
        return new PullRequestPopup(branch, pullRequest, Option.none());
    }

    PullRequestPopup with(Either<Failure, PullRequestDetails> read) {
        return new PullRequestPopup(branch, pullRequest, Option.of(read));
    }
}
