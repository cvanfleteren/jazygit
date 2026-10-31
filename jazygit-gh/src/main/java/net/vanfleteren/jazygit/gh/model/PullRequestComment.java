package net.vanfleteren.jazygit.gh.model;

import io.vavr.control.Option;
import java.time.Instant;

/**
 * A part of the conversation on a pull request: its description, a comment on it, or a review of it.
 *
 * @param id     tells it apart from the rest of the conversation
 * @param author the login of who wrote it, without {@code @}; empty when GitHub does not say, as for a deleted
 *               account
 * @param at     when it was written
 * @param body   what it says, as Markdown; empty for a review without text
 * @param edited whether it was changed after it was written
 * @param hidden why GitHub hides it, as GitHub says it (e.g. {@code outdated}), or an empty reason; none when shown
 * @param rights what the viewer may do with it: with a comment, what they may with what they wrote, as
 *               {@code gh pr view} says no more; with the description or a review, nothing (ADR 64)
 */
public record PullRequestComment(
        Id id,
        Kind kind,
        String author,
        Instant at,
        String body,
        boolean edited,
        Option<String> hidden,
        CommentRights rights) {

    /**
     * The id of the description, which GitHub gives none.
     */
    public static final Id DESCRIPTION = new Id("description");

    public record Id(String value) {}

    /**
     * What it is: the description, a comment, or a review, which approves, asks for changes, comments, or was
     * dismissed.
     */
    public enum Kind {
        DESCRIPTION,
        COMMENT,
        APPROVED,
        CHANGES_REQUESTED,
        COMMENTED,
        DISMISSED
    }
}
