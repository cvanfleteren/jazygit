package net.vanfleteren.jazygit.gh.model;

/**
 * The open pull requests that concern the account gh runs as, in any repository.
 *
 * @param reviewRequested the ones that ask for its review
 * @param authored        the ones it opened
 */
public record MyPullRequests(PullRequestList reviewRequested, PullRequestList authored) {}
