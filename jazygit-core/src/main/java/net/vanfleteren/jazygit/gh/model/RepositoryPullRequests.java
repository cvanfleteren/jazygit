package net.vanfleteren.jazygit.gh.model;

import io.vavr.control.Option;

/**
 * The open pull requests of a repository, forks included.
 *
 * @param repository the repository, as {@code owner/name}
 * @param open       its open pull requests
 * @param viewer     the login of the account they were read as, if GitHub said
 */
public record RepositoryPullRequests(String repository, PullRequestList open, Option<String> viewer) {

    /**
     * Read as an account GitHub did not name.
     */
    public RepositoryPullRequests(String repository, PullRequestList open) {
        this(repository, open, Option.none());
    }
}
