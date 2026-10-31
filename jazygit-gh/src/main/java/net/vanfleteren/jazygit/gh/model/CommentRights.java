package net.vanfleteren.jazygit.gh.model;

/**
 * What the viewer may do with a comment, of a review thread or of the conversation on a pull request (ADR 64).
 *
 * @param edit   whether they may change its text
 * @param delete whether they may delete it
 */
public record CommentRights(boolean edit, boolean delete) {

    /**
     * Every right.
     */
    public static final CommentRights ALL = new CommentRights(true, true);

    /**
     * No right.
     */
    public static final CommentRights NONE = new CommentRights(false, false);
}
