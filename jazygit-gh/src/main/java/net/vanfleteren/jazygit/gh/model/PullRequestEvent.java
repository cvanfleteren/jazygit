package net.vanfleteren.jazygit.gh.model;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;

/**
 * PROTOTYPE (branch prototype/pr-panel): something that happened on a pull request, as its timeline tells.
 *
 * @param at      when
 * @param actor   who, empty when GitHub does not say
 * @param kind    what
 * @param subject what it was about in a few words: the label, the reviewer asked, the commit's headline
 * @param body    what was written, empty when nothing was
 * @param remarks the comments on lines of the diff a review made
 */
public record PullRequestEvent(
        Instant at, String actor, Kind kind, String subject, String body, List<Remark> remarks) {

    public enum Kind {
        COMMENTED,
        APPROVED,
        CHANGES_REQUESTED,
        REVIEWED,
        COMMITTED,
        FORCE_PUSHED,
        REVIEW_REQUESTED,
        READY_FOR_REVIEW,
        CONVERTED_TO_DRAFT,
        LABELED,
        MERGED,
        CLOSED,
        REOPENED
    }

    /**
     * A comment on a line of the diff.
     */
    public record Remark(String path, Option<Integer> line, String body) {}
}
