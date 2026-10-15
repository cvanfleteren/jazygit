package net.vanfleteren.jazygit.gh;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.MergeMethod;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * What the TUI does with GitHub, through the {@code gh} command line tool.
 */
public interface GitHub {

    /**
     * The open pull requests of the repository, from branches of the repository itself (not from forks).
     */
    Either<GhError, List<PullRequest>> pullRequests();

    /**
     * What there is to know about pull request {@code number}.
     */
    Either<GhError, PullRequestDetails> pullRequestDetails(int number);

    /**
     * Creates a pull request from the remote branch {@code head} into {@code base}; its title and description
     * are taken from the commits.
     */
    Either<GhFailure, Created> createPullRequest(String head, String base);

    /**
     * A pull request that was created.
     *
     * @param number   its number, if gh said where it is
     * @param commands the command log
     */
    record Created(Option<Integer> number, List<ExecutedCommand> commands) {
    }

    /**
     * Opens pull request {@code number} in the browser.
     */
    Either<GhFailure, List<ExecutedCommand>> openPullRequest(int number);

    /**
     * Opens {@code branch}, as it is named on the remote, in the browser.
     */
    Either<GhFailure, List<ExecutedCommand>> browse(String branch);

    /**
     * Merges pull request {@code number} as {@code method} says, keeping its branch.
     */
    Either<GhFailure, List<ExecutedCommand>> mergePullRequest(int number, MergeMethod method);

    /**
     * Closes pull request {@code number} without merging it.
     */
    Either<GhFailure, List<ExecutedCommand>> closePullRequest(int number);
}
