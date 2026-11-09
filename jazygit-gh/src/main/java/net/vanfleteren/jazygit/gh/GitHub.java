package net.vanfleteren.jazygit.gh;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.gh.model.*;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * What the TUI does with GitHub, through the {@code gh} command line tool.
 */
public interface GitHub {

    /**
     * The most bytes of an image that are read (ADR 84): more than a screenshot of a large screen takes.
     */
    int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

    /**
     * The version of the gh at {@code executable}, as {@code gh --version} prints it first.
     */
    Either<GhError, String> version(String executable);

    /**
     * The accounts gh is logged in to, on every host, whatever account the repository is configured to use.
     */
    Either<GhError, List<GhLogin>> logins();

    /**
     * The open pull requests of the repository, also the ones from forks, the most recently updated first; the
     * branches show the others ({@link RepositoryPullRequests#ofBranches()}).
     */
    Either<GhError, RepositoryPullRequests> repositoryPullRequests();

    /**
     * The open pull requests that ask for the review of the account gh runs as, and the ones it opened, in any
     * repository, the most recently updated first.
     */
    Either<GhError, MyPullRequests> myPullRequests();

    /**
     * What the pull request at {@code url}, of any repository, says and changes.
     */
    Either<GhError, PullRequestPreview> pullRequestPreview(String url);

    /**
     * How the CI checks are doing on the tip of each branch of the repository.
     */
    Either<GhError, BranchChecks> branchChecks();

    /**
     * The names of the protected branches of the repository (ADR 72).
     */
    Either<GhError, List<String>> protectedBranches();

    /**
     * What there is to know about pull request {@code number}.
     */
    Either<GhError, PullRequestDetails> pullRequestDetails(int number);

    /**
     * The checks on the tips of {@code branches} of the repository, one by one and with their timing.
     */
    Either<GhError, BranchCheckRuns> branchCheckRuns(List<String> branches);

    /**
     * The checks that ran lately on the repository, to learn how long they take from: on the pull requests merged
     * last and on the last commits of its default branch.
     */
    Either<GhError, RecentChecks> recentChecks();

    /**
     * The conversation on pull request {@code number}: its description, unless it is empty, then its comments and
     * the reviews that say something, oldest first. A review says something when it has text, approves, asks for
     * changes or was dismissed; pending reviews, which only their author sees, are left out.
     */
    Either<GhError, List<PullRequestComment>> pullRequestComments(int number);

    /**
     * The review threads of pull request {@code number}, on lines of its diff or on whole files, with the commits its
     * changes are diffed between (ADR 55). The comments of a review that is not submitted yet, which only their author
     * sees, are left out, and so is a thread of only those.
     */
    Either<GhError, ReviewRemarks> reviewRemarks(int number);

    /**
     * What is at {@code url}, an image GitHub keeps, as bytes, up to {@link #MAX_IMAGE_BYTES}: more is too much
     * output (ADR 84).
     */
    Either<GhError, byte[]> image(String url);

    /**
     * Creates a pull request from the remote branch {@code head} into {@code base}, as {@code pullRequest} says.
     */
    Either<GhFailure, Created> createPullRequest(GhBranch head, GhBranch base, NewPullRequest pullRequest);

    /**
     * Opens pull request {@code number} in the browser.
     */
    Either<GhFailure, List<ExecutedCommand>> openPullRequest(int number);

    /**
     * Opens the pull request at {@code url}, of any repository, in the browser.
     */
    Either<GhFailure, List<ExecutedCommand>> openPullRequest(String url);

    /**
     * Opens the pull request, or the review comment, {@code link} is to in the browser set for the account the
     * repository uses (ADR 88), else the one gh would open it in, started by jazygit, as gh drops the anchor of a link
     * (ADR 85). Without a browser set, on another system than Linux, gh opens the pull request, without the anchor;
     * so does it when the host is not known.
     */
    Either<GhFailure, List<ExecutedCommand>> openInBrowser(PullRequestLink link);

    /**
     * Opens {@code url}, a link to anywhere, in the browser set for the account the repository uses (ADR 88), else the
     * one gh would open a link to the repository's host in, started by jazygit (ADR 96). gh opens no link of its own
     * choosing, so without either it fails with {@link GhError.NoBrowser}.
     */
    Either<GhFailure, List<ExecutedCommand>> openLink(String url);

    /**
     * The account gh runs as for the repository now, on the host its remotes give, an ssh alias resolved: the one it
     * is configured to use, else gh's active one; none when that is not known, as when a token in the environment
     * makes gh run as another (ADR 88).
     */
    Option<GhAccount> account();

    /**
     * The browser gh would open a link to {@code host} in, and what chose it; none when gh would leave it to the
     * system (ADR 85).
     */
    Option<ChosenBrowser> ghBrowser(String host);

    /**
     * Opens {@code branch}, as it is named on the remote, in the browser.
     */
    Either<GhFailure, List<ExecutedCommand>> browse(GhBranch branch);

    /**
     * Checks out pull request {@code number} of the repository in the working tree, from a fork too, as gh does:
     * on a local branch of its own, which follows the branch it comes from.
     */
    Either<GhFailure, List<ExecutedCommand>> checkoutPullRequest(int number);

    /**
     * Checks out pull request {@code number} as {@link #checkoutPullRequest(int)} does, but in the worktree
     * {@code in}, taken relative to the working tree of this one.
     */
    Either<GhFailure, List<ExecutedCommand>> checkoutPullRequest(int number, Path in);

    /**
     * Has GitHub merge pull request {@code number} in the background as {@code method} says, keeping its branch,
     * together with the ones below it in its stack: only while its branch is still at {@code head}, if given, so
     * what was pushed since it was read is not merged.
     */
    Either<GhFailure, Merging> mergePullRequest(int number, Option<GhCommit> head, MergeMethod method);

    /**
     * How the merge {@code id} of pull request {@code number} is doing.
     */
    Either<GhError, MergeStatus> mergeStatus(int number, String id);

    /**
     * Closes pull request {@code number} without merging it.
     */
    Either<GhFailure, List<ExecutedCommand>> closePullRequest(int number);

    /**
     * Turns pull request {@code number} into a draft when {@code draft}, else marks it ready for review.
     */
    Either<GhFailure, List<ExecutedCommand>> setDraft(int number, boolean draft);

    /**
     * Changes what {@code edit} gives of pull request {@code number}: its title, its description and its base.
     */
    Either<GhFailure, List<ExecutedCommand>> editPullRequest(int number, PullRequestEdit edit);

    /**
     * Makes a stack on GitHub of {@code pullRequests}, the bottom one first: two or more, each with the head of the one
     * below it as its base (ADR 73).
     */
    Either<GhFailure, List<ExecutedCommand>> createStack(List<Integer> pullRequests);

    /**
     * Puts {@code pullRequests}, the lowest one first, on the top of stack {@code stack} (ADR 73).
     */
    Either<GhFailure, List<ExecutedCommand>> addToStack(int stack, List<Integer> pullRequests);

    /**
     * Takes stack {@code stack} apart on GitHub; its pull requests stay open, with their bases (ADR 73).
     */
    Either<GhFailure, List<ExecutedCommand>> unstack(int stack);

    /**
     * Adds a comment saying {@code body} to the conversation of pull request {@code number}.
     */
    Either<GhFailure, List<ExecutedCommand>> commentOnPullRequest(int number, String body);

    /**
     * Starts {@code thread} on pull request {@code number}, at its head commit {@code commit}, published at once
     * (ADR 60).
     */
    Either<GhFailure, List<ExecutedCommand>> startReviewThread(int number, String commit, NewReviewThread thread);

    /**
     * Replies {@code body} to the review thread of pull request {@code number} whose first comment has the id
     * {@code comment}, as REST numbers it, published at once (ADR 60).
     */
    Either<GhFailure, List<ExecutedCommand>> replyToReviewThread(int number, String comment, String body);

    /**
     * Resolves the review thread with node id {@code thread} when {@code resolved}, else unresolves it (ADR 60).
     */
    Either<GhFailure, List<ExecutedCommand>> resolveReviewThread(String thread, boolean resolved);

    /**
     * Deletes the comment of a review thread with node id {@code comment}; one that is gone already counts as deleted
     * (ADR 64).
     */
    Either<GhFailure, List<ExecutedCommand>> deleteReviewComment(String comment);

    /**
     * Deletes the comment of the conversation on a pull request with node id {@code comment}; one that is gone already
     * counts as deleted (ADR 64).
     */
    Either<GhFailure, List<ExecutedCommand>> deletePullRequestComment(String comment);

    /**
     * Gives {@code reaction} to the comment of a review thread with node id {@code comment} when {@code given}, else
     * takes it back (ADR 86); a comment GitHub no longer has fails as {@link GhError.Gone}.
     */
    Either<GhFailure, List<ExecutedCommand>> reactToReviewComment(String comment, Reaction reaction, boolean given);

    /**
     * Changes the comment of a review thread with node id {@code comment} to {@code body} (ADR 65).
     */
    Either<GhFailure, Edited> editReviewComment(String comment, String body);

    /**
     * Changes the comment of the conversation on a pull request with node id {@code comment} to {@code body}
     * (ADR 65).
     */
    Either<GhFailure, Edited> editPullRequestComment(String comment, String body);

    /**
     * A pull request that was created.
     *
     * @param number   its number, if gh said where it is
     * @param commands the command log
     */
    record Created(Option<Integer> number, List<ExecutedCommand> commands) {}

    /**
     * A merge GitHub was asked for.
     *
     * @param status   how it is doing: pending, unless GitHub was done with it straight away
     * @param commands the command log
     */
    record Merging(MergeStatus status, List<ExecutedCommand> commands) {}

    /**
     * A comment that was edited.
     *
     * @param body     its body, as GitHub now holds it
     * @param commands the command log
     */
    record Edited(String body, List<ExecutedCommand> commands) {}
}
