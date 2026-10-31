package net.vanfleteren.jazygit.gh.model;

import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * The open pull requests of a repository, forks included.
 *
 * @param repository the repository, as {@code owner/name}
 * @param open       its open pull requests
 * @param viewer     the login of the account they were read as, if GitHub said
 * @param latest     the number of its pull request opened last, open or not; 0 when it has none, or GitHub did not
 *                   say
 */
public record RepositoryPullRequests(String repository, PullRequestList open, Option<String> viewer, int latest) {

    /**
     * Read as an account GitHub did not name, without the pull request opened last.
     */
    public RepositoryPullRequests(String repository, PullRequestList open) {
        this(repository, open, Option.none(), 0);
    }

    /**
     * The open pull requests from branches of the repository itself, not from forks, as the branches show them: the
     * same read serves both (ADR 58).
     */
    public List<PullRequest> ofBranches() {
        return open.pullRequests().filter(summary -> !summary.fromFork()).map(PullRequestSummary::pullRequest);
    }
}
