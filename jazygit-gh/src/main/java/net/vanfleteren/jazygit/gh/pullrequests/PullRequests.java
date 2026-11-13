package net.vanfleteren.jazygit.gh.pullrequests;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.gh.model.*;
import net.vanfleteren.jazygit.gh.process.GhError;
import net.vanfleteren.jazygit.gh.process.GhFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * The pull requests: listing and reading them, and creating, checking out, merging, closing and editing them
 * (ADR 114).
 */
public interface PullRequests {

    /**
     * The most bytes of an image that are read (ADR 84): more than a screenshot of a large screen takes.
     */
    int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

    /**
     * The open pull requests of the repository, also the ones from forks, the most recently updated first; the
     * branches show the others ({@link RepositoryPullRequests#ofBranches()}).
     */
    Either<GhError, RepositoryPullRequests> ofRepository();

    /**
     * The open pull requests that ask for the review of the account gh runs as, and the ones it opened, in any
     * repository, the most recently updated first.
     */
    Either<GhError, MyPullRequests> mine();

    /**
     * What the pull request at {@code url}, of any repository, says and changes.
     */
    Either<GhError, PullRequestPreview> preview(String url);

    /**
     * What there is to know about pull request {@code number}.
     */
    Either<GhError, PullRequestDetails> details(int number);

    /**
     * What is at {@code url}, an image GitHub keeps, as bytes, up to {@link #MAX_IMAGE_BYTES}: more is too much
     * output (ADR 84).
     */
    Either<GhError, byte[]> image(String url);

    /**
     * Creates a pull request from the remote branch {@code head} into {@code base}, as {@code pullRequest} says.
     */
    Either<GhFailure, Created> create(GhBranch head, GhBranch base, NewPullRequest pullRequest);

    /**
     * Checks out pull request {@code number} of the repository in the working tree, from a fork too, as gh does:
     * on a local branch of its own, which follows the branch it comes from.
     */
    Either<GhFailure, List<ExecutedCommand>> checkout(int number);

    /**
     * Checks out pull request {@code number} as {@link #checkout(int)} does, but in the worktree {@code in}, taken
     * relative to the working tree of this one.
     */
    Either<GhFailure, List<ExecutedCommand>> checkout(int number, Path in);

    /**
     * Has GitHub merge pull request {@code number} in the background as {@code method} says, keeping its branch,
     * together with the ones below it in its stack: only while its branch is still at {@code head}, if given, so
     * what was pushed since it was read is not merged.
     */
    Either<GhFailure, Merging> merge(int number, Option<GhCommit> head, MergeMethod method);

    /**
     * How the merge {@code id} of pull request {@code number} is doing.
     */
    Either<GhError, MergeStatus> mergeStatus(int number, String id);

    /**
     * Closes pull request {@code number} without merging it.
     */
    Either<GhFailure, List<ExecutedCommand>> close(int number);

    /**
     * Turns pull request {@code number} into a draft when {@code draft}, else marks it ready for review.
     */
    Either<GhFailure, List<ExecutedCommand>> setDraft(int number, boolean draft);

    /**
     * Changes what {@code edit} gives of pull request {@code number}: its title, its description and its base.
     */
    Either<GhFailure, List<ExecutedCommand>> edit(int number, PullRequestEdit edit);

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
