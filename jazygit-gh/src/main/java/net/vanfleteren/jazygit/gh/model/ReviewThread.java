package net.vanfleteren.jazygit.gh.model;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;

/**
 * A thread of a review on a pull request, about a line of a file of its diff, or about a whole file (ADR 55).
 *
 * @param path         the path of its file, as it is in the head of the pull request
 * @param side         on which side of the diff its line is: in the old file or the new one
 * @param line         the number of its line, its last when it is about several; none when it is outdated, or about
 *                     a whole file
 * @param startLine    the number of its first line when it is about several
 * @param startSide    on which side of the diff its first line is, which may be the other one than that of its last
 * @param originalLine the number its line had when the thread was made, in the commit it was made on
 * @param resolved     whether it is resolved
 * @param outdated     whether its line changed since it was made
 * @param subject      whether it is about a line or a whole file
 * @param comments     its comments, oldest first, as far as they were read, those of a review not submitted yet left out
 * @param more         how many more comments it has than were read
 * @param id           its node id, which GitHub's mutations of it take (ADR 60)
 * @param rights       what the viewer may do with it
 */
public record ReviewThread(
        String path,
        Side side,
        Option<Integer> line,
        Option<Integer> startLine,
        Side startSide,
        Option<Integer> originalLine,
        boolean resolved,
        boolean outdated,
        Subject subject,
        List<Comment> comments,
        int more,
        String id,
        Rights rights) {

    /**
     * Whether it is on the file with the path {@code path}, renamed from {@code origin} if it was: GitHub gives the
     * new path of a renamed file, but git may pair it with another than GitHub does, and the old path of a deleted
     * one, which is also its path in git (ADR 55). Wherever a thread is matched to a file, it is matched by this.
     */
    public boolean isOn(String path, Option<String> origin) {
        return path.equals(this.path) || origin.contains(this.path);
    }

    /**
     * Which file of a diff a line is numbered in.
     */
    public enum Side {
        /**
         * The old file, which a removed line is of.
         */
        LEFT,
        /**
         * The new file, which an added line is of.
         */
        RIGHT
    }

    /**
     * What a thread is about.
     */
    public enum Subject {
        LINE,
        FILE
    }

    /**
     * A comment of a thread.
     *
     * @param author who wrote it, empty when GitHub does not say, as for a deleted account
     * @param at     when it was written
     * @param body   its text, in Markdown
     * @param hidden why GitHub hides it (outdated, resolved, off-topic, spam…), if it does
     * @param id     its node id, which editing or deleting it takes (ADR 60)
     * @param restId its id as REST numbers it, which a reply to the thread it starts is sent to (ADR 60)
     * @param rights what the viewer may do with it (ADR 64)
     */
    public record Comment(
            String author,
            Instant at,
            String body,
            Option<String> hidden,
            String id,
            String restId,
            CommentRights rights) {

        /**
         * This comment saying {@code body}, as when it was edited (ADR 65).
         */
        public Comment withBody(String body) {
            return new Comment(author, at, body, hidden, id, restId, rights);
        }
    }

    /**
     * What the viewer may do with a thread, as GitHub says (ADR 60).
     *
     * @param reply     whether they may reply to it
     * @param resolve   whether they may resolve it
     * @param unresolve whether they may unresolve it
     */
    public record Rights(boolean reply, boolean resolve, boolean unresolve) {

        /**
         * Every right.
         */
        public static final Rights ALL = new Rights(true, true, true);
    }
}
