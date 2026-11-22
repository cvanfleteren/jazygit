package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequest;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Showing the pull request on Forgejo that merges {@code branch} (ADR 176).
 *
 * @param branch        the branch of a remote it merges, which the branch shown is pushed to
 * @param pullRequest   what is known of it from the list of the open pull requests
 * @param details       what was read of it, or why that failed; nothing while it is first being read
 * @param asked         whether it is being read again as the user asked, while {@code details} stay shown
 * @param refreshFailed why reading it again failed the last time, so the details shown are stale; nothing once a
 *                      read succeeds
 * @param read          the read of it it waits for (ADR 34)
 */
public record ForgejoPullRequestPopup(
        RemoteBranch branch,
        ForgejoPullRequest pullRequest,
        Option<Either<Failure, ShownPullRequest>> details,
        boolean asked,
        Option<Failure> refreshFailed,
        long read)
        implements Popup {

    /**
     * The popup while its pull request is first read, by read {@code read}.
     */
    public static ForgejoPullRequestPopup loading(RemoteBranch branch, ForgejoPullRequest pullRequest, long read) {
        return new ForgejoPullRequestPopup(branch, pullRequest, Option.none(), false, Option.none(), read);
    }

    /**
     * The popup once read {@code id} brought {@code result}; as it is for a read it no longer waits for. Details that
     * were read already stay shown when reading them again fails, marked stale.
     */
    public ForgejoPullRequestPopup with(Either<Failure, ShownPullRequest> result, long id) {
        if (id != read) {
            return this;
        }
        return result.fold(
                failure -> details.exists(Either::isRight)
                        ? new ForgejoPullRequestPopup(branch, pullRequest, details, false, Option.of(failure), read)
                        : new ForgejoPullRequestPopup(
                                branch, pullRequest, Option.of(result), false, Option.none(), read),
                shown -> new ForgejoPullRequestPopup(
                        branch, pullRequest, Option.of(result), false, Option.none(), read));
    }

    /**
     * The popup while it is read again, as the user asked, by read {@code id}.
     */
    public ForgejoPullRequestPopup readingAgain(long id) {
        return new ForgejoPullRequestPopup(branch, pullRequest, details, true, refreshFailed, id);
    }

    /**
     * Whether its pull request is being read: the first time, or again as the user asked.
     */
    public boolean reading() {
        return details.isEmpty() || asked;
    }

    /**
     * What was read of it, when that succeeded.
     */
    public Option<ShownPullRequest> shown() {
        return details.flatMap(Either::toOption);
    }

    /**
     * Where it is on the web: as read, or as listed before that.
     */
    public String url() {
        return shown().map(read -> read.pullRequest().url())
                .filter(url -> !url.isEmpty())
                .getOrElse(pullRequest::url);
    }
}
