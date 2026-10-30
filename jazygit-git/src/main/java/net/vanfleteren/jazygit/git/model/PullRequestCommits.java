package net.vanfleteren.jazygit.git.model;

/**
 * The commits a pull request on GitHub is diffed between, as GitHub shows its changes: its head commit against the
 * merge base of that and its base commit (ADR 55).
 *
 * @param number     its number, which names its head on the remote: {@code refs/pull/<number>/head}
 * @param repository the repository it is of, as {@code owner/name}, which names the remote it is fetched from
 * @param host       the host that repository is on, like {@code github.com}, which the remote is on; empty when not
 *                   known
 * @param base       the commit its base branch is at
 * @param head       the commit its branch is at
 */
public record PullRequestCommits(int number, String repository, String host, Sha base, Sha head) {}
