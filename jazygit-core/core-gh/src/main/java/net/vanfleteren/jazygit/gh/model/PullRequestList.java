package net.vanfleteren.jazygit.gh.model;

import io.vavr.collection.List;

/**
 * Some of the pull requests that match what was asked for, the most recently updated first.
 *
 * @param pullRequests the ones read, at most as many as were asked for
 * @param total        how many match, also the ones not read
 */
public record PullRequestList(List<PullRequestSummary> pullRequests, int total) {

    public static final PullRequestList EMPTY = new PullRequestList(List.empty(), 0);

    /**
     * Whether more match than were read.
     */
    public boolean truncated() {
        return total > pullRequests.size();
    }
}
