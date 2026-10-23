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
     * The version of the gh at {@code executable}, as {@code gh --version} prints it first.
     */
    Either<GhError, String> version(String executable);

    /**
     * The accounts gh is logged in to, on every host, whatever account the repository is configured to use.
     */
    Either<GhError, List<GhLogin>> logins();

    /**
     * The open pull requests of the repository, from branches of the repository itself (not from forks).
     */
    Either<GhError, List<PullRequest>> pullRequests();

    /**
     * The open pull requests of the repository, also the ones from forks, the most recently updated first.
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
     * What there is to know about pull request {@code number}.
     */
    Either<GhError, PullRequestDetails> pullRequestDetails(int number);

    /**
     * The conversation on pull request {@code number}: its description, unless it is empty, then its comments and
     * the reviews that say something, oldest first. A review says something when it has text, approves, asks for
     * changes or was dismissed; pending reviews, which only their author sees, are left out.
     */
    Either<GhError, List<PullRequestComment>> pullRequestComments(int number);

    /**
     * Creates a pull request from the remote branch {@code head} into {@code base}, as {@code pullRequest} says.
     */
    Either<GhFailure, Created> createPullRequest(String head, String base, NewPullRequest pullRequest);

    /**
     * Opens pull request {@code number} in the browser.
     */
    Either<GhFailure, List<ExecutedCommand>> openPullRequest(int number);

    /**
     * Opens the pull request at {@code url}, of any repository, in the browser.
     */
    Either<GhFailure, List<ExecutedCommand>> openPullRequest(String url);

    /**
     * Opens {@code branch}, as it is named on the remote, in the browser.
     */
    Either<GhFailure, List<ExecutedCommand>> browse(String branch);

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
     * together with the ones below it in its stack: only while its branch is still at {@code head}, unless that is
     * empty, so what was pushed since it was read is not merged.
     */
    Either<GhFailure, Merging> mergePullRequest(int number, String head, MergeMethod method);

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
     * Adds a comment saying {@code body} to the conversation of pull request {@code number}.
     */
    Either<GhFailure, List<ExecutedCommand>> commentOnPullRequest(int number, String body);

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
}
