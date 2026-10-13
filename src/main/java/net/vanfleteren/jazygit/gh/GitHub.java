package net.vanfleteren.jazygit.gh;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.gh.model.PullRequest;
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
     * Creates a pull request from the remote branch {@code head} into {@code base}; its title and description
     * are taken from the commits.
     */
    Either<GhFailure, List<ExecutedCommand>> createPullRequest(String head, String base);

    /**
     * Opens pull request {@code number} in the browser.
     */
    Either<GhFailure, List<ExecutedCommand>> openPullRequest(int number);
}
