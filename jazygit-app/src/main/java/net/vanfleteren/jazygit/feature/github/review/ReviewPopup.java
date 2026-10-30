package net.vanfleteren.jazygit.feature.github.review;

import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.gh.model.NewReviewThread;
import net.vanfleteren.jazygit.state.Popup;
import net.vanfleteren.jazygit.state.Scrolled;

/**
 * Asking for a comment on the lines of the diff of a pull request's files, or a reply to one of its threads (ADR 60).
 *
 * @param number  the number of the pull request
 * @param target  what the comment goes to
 * @param where   where it goes, as the title says it: the path of the file, and the lines
 * @param quoted  the lines the comment is about, or the last comment of the thread replied to
 * @param text    the comment as far as it was typed, or as it came back from the editor
 * @param sending whether it is being sent; it can then not be changed, nor sent again
 * @param area    the id of the area of the content panel the diff is shown in
 * @param at      where that area was scrolled to, and its cursor, when it was asked for
 */
@With
public record ReviewPopup(
        int number,
        Target target,
        Where where,
        List<String> quoted,
        String text,
        boolean sending,
        String area,
        Scrolled at)
        implements Popup {

    @Override
    public boolean holdsInput() {
        return !text.isBlank();
    }

    /**
     * What a comment goes to.
     */
    public sealed interface Target {

        /**
         * A reply to the thread with the id {@code thread}.
         */
        record Reply(String thread) implements Target {}

        /**
         * A thread to start on the pull request with the node id {@code pullRequest}, on {@code lines} of the file
         * {@code path}, or on the whole file.
         */
        record Start(String pullRequest, String path, Option<NewReviewThread.Lines> lines) implements Target {}
    }

    /**
     * Where a comment goes, as the title of the popup says it.
     *
     * @param path  the path of its file
     * @param lines the numbers of its first and its last line, if it is on lines
     * @param reply whether it is a reply
     */
    public record Where(String path, Option<Lines> lines, boolean reply) {}

    /**
     * The numbers of the first and the last line of a comment.
     */
    public record Lines(int first, int last) {}
}
