package net.vanfleteren.jazygit.gh.model;

import io.vavr.collection.List;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;

/**
 * Everything shown about one pull request, read when the user asks for it.
 *
 * @param number       its number, e.g. 42 for {@code #42}
 * @param title        its title
 * @param author       the login of who opened it, empty when GitHub does not say
 * @param state        whether it is open, closed or merged
 * @param draft        whether it is still a draft
 * @param headBranch   the branch on the remote it merges from
 * @param baseBranch   the branch it merges into
 * @param createdAt    when it was opened
 * @param additions    the lines it adds
 * @param deletions    the lines it removes
 * @param changedFiles the files it changes
 * @param mergeable    whether it merges without conflicts
 * @param review       where its review stands
 * @param comments     how many comments it has
 * @param reviewThreads its review threads, on lines of the diff
 * @param labels       its labels
 * @param reviewers    who reviewed it or is asked to
 * @param checks       its CI checks, one by one
 */
public record PullRequestDetails(
        int number,
        String title,
        String author,
        State state,
        boolean draft,
        String headBranch,
        String baseBranch,
        Instant createdAt,
        int additions,
        int deletions,
        int changedFiles,
        Mergeable mergeable,
        Review review,
        int comments,
        ReviewThreads reviewThreads,
        List<String> labels,
        List<Reviewer> reviewers,
        List<Check> checks) {

    /**
     * This pull request, as a draft when {@code draft}, else as ready for review.
     */
    public PullRequestDetails withDraft(boolean draft) {
        return new PullRequestDetails(
                number,
                title,
                author,
                state,
                draft,
                headBranch,
                baseBranch,
                createdAt,
                additions,
                deletions,
                changedFiles,
                mergeable,
                review,
                comments,
                reviewThreads,
                labels,
                reviewers,
                checks);
    }

    public enum State {
        OPEN,
        CLOSED,
        MERGED
    }

    public enum Mergeable {
        MERGEABLE,
        CONFLICTING,
        /**
         * GitHub has not worked it out yet.
         */
        UNKNOWN
    }

    /**
     * The latest review of one reviewer.
     */
    public enum ReviewerState {
        APPROVED,
        CHANGES_REQUESTED,
        COMMENTED,
        /**
         * Asked to review, and has not yet.
         */
        REQUESTED
    }

    /**
     * Someone who reviewed the pull request, or is asked to.
     *
     * @param login their login, or the name of a team
     */
    public record Reviewer(String login, ReviewerState state) {}

    /**
     * The review threads of a pull request: the inline comments on lines of its diff, outdated ones too.
     *
     * @param threads    how many threads there are
     * @param unresolved how many of them are not resolved
     * @param comments   how many comments they hold, the replies too
     */
    public record ReviewThreads(int threads, int unresolved, int comments) {

        public static final ReviewThreads NONE = new ReviewThreads(0, 0, 0);
    }

    /**
     * One CI check.
     *
     * @param name  the name of the check run, or the context of the commit status
     * @param state how it is doing; never {@link Checks#NONE}
     */
    public record Check(String name, Checks state) {}
}
