package net.vanfleteren.jazygit.gh.model;

/**
 * What the viewer may do with a comment, of a review thread or of the conversation on a pull request (ADR 64).
 *
 * @param edit   whether they may change its text
 * @param delete whether they may delete it
 * @param react  whether they may react to it (ADR 86); GitHub refuses it on a locked conversation or an archived
 *               repository, also to who may read the comment
 */
public record CommentRights(boolean edit, boolean delete, boolean react) {

    /**
     * Every right.
     */
    public static final CommentRights ALL = new CommentRights(true, true, true);

    /**
     * No right.
     */
    public static final CommentRights NONE = new CommentRights(false, false, false);
}
