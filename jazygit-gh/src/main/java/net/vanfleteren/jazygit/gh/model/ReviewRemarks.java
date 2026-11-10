package net.vanfleteren.jazygit.gh.model;

import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * The review threads of a pull request, and the commits its changes were diffed between when they were read
 * (ADR 55).
 *
 * @param state   whether it is open, merged or closed
 * @param head    the commit its branch is at, which GitHub keeps after the branch is deleted; none when it cannot be
 *                read
 * @param base    the commit its base branch is at
 * @param threads   its review threads, those of a review not submitted yet left out
 * @param draft     whether it is still a draft
 * @param reviewing what the viewer needs to approve it or ask for changes from its diff (ADR 100)
 */
public record ReviewRemarks(
        PullRequestDetails.State state,
        Option<GhCommit> head,
        Option<GhCommit> base,
        List<ReviewThread> threads,
        boolean draft,
        PullRequestDetails.Reviewing reviewing) {

    /**
     * Remarks of a pull request that is no draft, which nothing is known to review by.
     */
    public ReviewRemarks(
            PullRequestDetails.State state, Option<GhCommit> head, Option<GhCommit> base, List<ReviewThread> threads) {
        this(state, head, base, threads, false, PullRequestDetails.Reviewing.UNKNOWN);
    }

    /**
     * No threads, of an open pull request whose commits are not known.
     */
    public static ReviewRemarks none() {
        return new ReviewRemarks(PullRequestDetails.State.OPEN, Option.none(), Option.none(), List.empty());
    }

    /**
     * These remarks with {@code threads} in place of their own, as when a comment of one was edited or deleted.
     */
    public ReviewRemarks withThreads(List<ReviewThread> threads) {
        return new ReviewRemarks(state, head, base, threads, draft, reviewing);
    }
}
