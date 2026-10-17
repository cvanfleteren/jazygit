package net.vanfleteren.jazygit.gh.model;

/**
 * The open pull requests of a repository, forks included.
 *
 * @param repository the repository, as {@code owner/name}
 * @param open       its open pull requests
 */
public record RepositoryPullRequests(String repository, PullRequestList open) {}
