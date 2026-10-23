package net.vanfleteren.jazygit.gh.model;

import io.vavr.collection.List;
import io.vavr.control.Option;
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
 * @param pushedAt     when its branch was last pushed to, as near as GitHub tells: when it was opened, its latest
 *                     commit or its latest force push, whichever is latest; a commit made before the pull request
 *                     was opened was pushed by then
 * @param additions    the lines it adds
 * @param deletions    the lines it removes
 * @param changedFiles the files it changes
 * @param commits      the commits it has, as its "Commits" tab on GitHub counts them
 * @param mergeable    whether it merges without conflicts
 * @param review       where its review stands
 * @param comments     how many comments it has
 * @param reviewThreads its review threads, on lines of the diff
 * @param labels       its labels
 * @param reviewers    who reviewed it or is asked to
 * @param checks       its CI checks, one by one
 * @param head         the commit its branch is at, empty when not known
 * @param stack        where it sits in its stack, if it is stacked
 * @param behindBase   how many commits its base branch has that its branch does not, as the base moved on since its
 *                     branch was made; empty when not known
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
        Instant pushedAt,
        int additions,
        int deletions,
        int changedFiles,
        int commits,
        Mergeable mergeable,
        Review review,
        int comments,
        ReviewThreads reviewThreads,
        List<String> labels,
        List<Reviewer> reviewers,
        List<Check> checks,
        String head,
        Option<PullRequestStack> stack,
        Option<Integer> behindBase) {

    /**
     * One that is not stacked, at a commit that is not known, not known to be behind its base.
     */
    public PullRequestDetails(
            int number,
            String title,
            String author,
            State state,
            boolean draft,
            String headBranch,
            String baseBranch,
            Instant createdAt,
            Instant pushedAt,
            int additions,
            int deletions,
            int changedFiles,
            int commits,
            Mergeable mergeable,
            Review review,
            int comments,
            ReviewThreads reviewThreads,
            List<String> labels,
            List<Reviewer> reviewers,
            List<Check> checks) {
        this(
                number,
                title,
                author,
                state,
                draft,
                headBranch,
                baseBranch,
                createdAt,
                pushedAt,
                additions,
                deletions,
                changedFiles,
                commits,
                mergeable,
                review,
                comments,
                reviewThreads,
                labels,
                reviewers,
                checks,
                "",
                Option.none(),
                Option.none());
    }

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
                pushedAt,
                additions,
                deletions,
                changedFiles,
                commits,
                mergeable,
                review,
                comments,
                reviewThreads,
                labels,
                reviewers,
                checks,
                head,
                stack,
                behindBase);
    }

    /**
     * This pull request, its base branch having {@code behind} commits its branch does not.
     */
    public PullRequestDetails withBehindBase(int behind) {
        return new PullRequestDetails(
                number,
                title,
                author,
                state,
                draft,
                headBranch,
                baseBranch,
                createdAt,
                pushedAt,
                additions,
                deletions,
                changedFiles,
                commits,
                mergeable,
                review,
                comments,
                reviewThreads,
                labels,
                reviewers,
                checks,
                head,
                stack,
                Option.of(behind));
    }

    /**
     * This pull request as a list of the open ones holds it; none when it is no longer open.
     */
    public Option<PullRequest> listed() {
        return Option.when(
                state == State.OPEN,
                () -> new PullRequest(
                        number, headBranch, draft, Checks.combined(checks.map(Check::state)), review, stack));
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
     * @param read       how many of them were read, the newest; fewer than {@code threads} on a pull request with
     *                   too many to read them all
     * @param unresolved how many of the ones read are not resolved
     * @param comments   how many comments the ones read hold, the replies too
     */
    public record ReviewThreads(int threads, int read, int unresolved, int comments) {

        public static final ReviewThreads NONE = new ReviewThreads(0, 0, 0, 0);

        /**
         * Whether every thread was read, so that what is counted of them is all there is.
         */
        public boolean complete() {
            return read == threads;
        }
    }

    /**
     * One CI check.
     *
     * @param name  the name of the check run, or the context of the commit status
     * @param state how it is doing; never {@link Checks#NONE}
     */
    public record Check(String name, Checks state) {}
}
