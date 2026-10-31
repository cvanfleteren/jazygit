package net.vanfleteren.jazygit.gh.model;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;

/**
 * A review thread to start on a pull request, with its first comment (ADR 60).
 *
 * @param path  the path of its file, as it is in the head of the pull request, or the old path of a deleted one
 * @param lines the lines it is on; none when it is about the whole file
 * @param body  its first comment, in Markdown
 */
public record NewReviewThread(String path, Option<Lines> lines, String body) {

    /**
     * The lines a thread is on: from its first, {@code startLine} on {@code startSide}, to its last, {@code line} on
     * {@code side}, both in one hunk. On one line, the first is the last.
     *
     * @param startSide on which side of the diff its first line is
     * @param startLine the number of its first line, on that side
     * @param side      on which side of the diff its last line is
     * @param line      the number of its last line, on that side
     */
    public record Lines(Side startSide, int startLine, Side side, int line) {

        /**
         * Whether it is on one line only.
         */
        public boolean single() {
            return startSide == side && startLine == line;
        }
    }
}
