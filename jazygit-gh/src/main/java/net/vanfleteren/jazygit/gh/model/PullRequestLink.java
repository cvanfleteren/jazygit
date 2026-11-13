package net.vanfleteren.jazygit.gh.model;

import io.vavr.control.Option;

/**
 * Where a pull request, or a review comment of it, is on GitHub's site (ADR 85).
 *
 * @param host       the host its repository is on, like {@code github.com}; empty when not known
 * @param repository the repository it is of, as {@code owner/name}
 * @param number     its number
 * @param comment    the REST id of the review comment to open it on, if any
 */
public record PullRequestLink(
        String host, String repository, PrNumber number, Option<ReviewThread.Comment.RestId> comment) {

    /**
     * The pull request's own URL, without an anchor.
     */
    public String pullRequest() {
        return "https://" + host + "/" + repository + "/pull/" + number.value();
    }

    /**
     * The URL of the comment, in the conversation of the pull request, which shows outdated and resolved threads too;
     * the pull request's own without one.
     */
    public String url() {
        return pullRequest() + comment.map(id -> "#discussion_r" + id.value()).getOrElse("");
    }
}
