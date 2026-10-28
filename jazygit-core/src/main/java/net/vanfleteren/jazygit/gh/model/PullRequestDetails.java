package net.vanfleteren.jazygit.gh.model;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import lombok.With;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;

/**
 * Everything shown about one pull request, read when the user asks for it, in the parts its dialog shows together
 * (ADR 42).
 *
 * @param number       its number, e.g. 42 for {@code #42}
 * @param title        its title
 * @param author       the login of who opened it, empty when GitHub does not say
 * @param status       whether it is open, a draft, and merges
 * @param branches     the branches it merges from and into, and how far apart they are
 * @param activity     when it was opened and last pushed to
 * @param size         how much it changes
 * @param conversation where its review stands, and what was said on it
 * @param checks       its CI checks, one by one
 * @param stack        where it sits in its stack, if it is stacked
 * @param repository   the repository it is of, as {@code owner/name}; empty when not known
 */
@With
public record PullRequestDetails(
        int number,
        String title,
        String author,
        Status status,
        Branches branches,
        Activity activity,
        Size size,
        Conversation conversation,
        List<Check> checks,
        Option<PullRequestStack> stack,
        String repository) {

    /**
     * One that is not stacked, of a repository that is not known.
     */
    public PullRequestDetails(
            int number,
            String title,
            String author,
            Status status,
            Branches branches,
            Activity activity,
            Size size,
            Conversation conversation,
            List<Check> checks) {
        this(number, title, author, status, branches, activity, size, conversation, checks, Option.none(), "");
    }

    /**
     * This pull request, as a draft when {@code draft}, else as ready for review.
     */
    public PullRequestDetails withDraft(boolean draft) {
        return withStatus(status.withDraft(draft));
    }

    /**
     * This pull request, its base branch having {@code behind} commits its branch does not.
     */
    public PullRequestDetails withBehindBase(int behind) {
        return withBranches(branches.withBehindBase(behind));
    }

    /**
     * This pull request as a list of the open ones holds it; none when it is no longer open.
     */
    public Option<PullRequest> listed() {
        return Option.when(
                status.state() == State.OPEN,
                () -> new PullRequest(
                        number,
                        branches.head(),
                        status.draft(),
                        Checks.combined(checks.map(Check::state)),
                        conversation.review(),
                        stack));
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
     * Whether a pull request is open, a draft, and merges.
     *
     * @param state     whether it is open, closed or merged
     * @param draft     whether it is still a draft
     * @param mergeable whether it merges without conflicts
     */
    @With
    public record Status(State state, boolean draft, Mergeable mergeable) {}

    /**
     * The branches a pull request merges from and into.
     *
     * @param head       the branch on the remote it merges from
     * @param base       the branch it merges into
     * @param headCommit the commit its branch is at, if known
     * @param behindBase how many commits its base branch has that its branch does not, as the base moved on since its
     *                   branch was made; empty when not known
     */
    public record Branches(String head, String base, Option<GhCommit> headCommit, Option<Integer> behindBase) {

        /**
         * Branches at a commit that is not known, not known to be apart.
         */
        public Branches(String head, String base) {
            this(head, base, Option.none(), Option.none());
        }

        public Branches withBehindBase(int behind) {
            return new Branches(head, base, headCommit, Option.of(behind));
        }
    }

    /**
     * When a pull request was opened and last pushed to.
     *
     * @param createdAt when it was opened
     * @param pushedAt  when its branch was last pushed to, as near as GitHub tells: when it was opened, its latest
     *                  commit or its latest force push, whichever is latest; a commit made before the pull request was
     *                  opened was pushed by then
     */
    public record Activity(Instant createdAt, Instant pushedAt) {}

    /**
     * How much a pull request changes.
     *
     * @param additions    the lines it adds
     * @param deletions    the lines it removes
     * @param changedFiles the files it changes
     * @param commits      the commits it has, as its "Commits" tab on GitHub counts them
     */
    public record Size(int additions, int deletions, int changedFiles, int commits) {}

    /**
     * Where the review of a pull request stands, and what was said on it.
     *
     * @param review        where its review stands
     * @param reviewers     who reviewed it or is asked to
     * @param comments      how many comments it has
     * @param reviewThreads its review threads, on lines of the diff
     * @param labels        its labels
     */
    public record Conversation(
            Review review, List<Reviewer> reviewers, int comments, ReviewThreads reviewThreads, List<String> labels) {}

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
     * @param state how it is doing; {@link Checks#NONE} for a status nobody set
     * @param run   when it ran, for a check run of a workflow; none for a commit status, which GitHub does not time
     */
    public record Check(String name, Checks state, Option<CheckRun> run) {

        /**
         * A check without timing.
         */
        public Check(String name, Checks state) {
            this(name, state, Option.none());
        }

        /**
         * Whether it is done: it passed or failed.
         */
        public boolean done() {
            return state == Checks.PASSING || state == Checks.FAILING;
        }

        /**
         * Whether it waits to start: a check run that is not done and did not start yet.
         */
        public boolean queued() {
            return !done() && run.exists(timed -> timed.startedAt().isEmpty());
        }
    }
}
