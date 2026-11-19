package net.vanfleteren.jazygit.content.diff;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.content.diff.RichDiff.Pending;

/**
 * How the hunks of a file are laid out, the one part of drawing a file ({@link FileSections}) that differs between the
 * unified view, {@link UnifiedHunk}, and the split view, {@link SplitHunk} (ADR 139): how wide the file is, where its
 * threads and markers go, and the lines of each of its hunks.
 */
sealed interface HunkLayout permits UnifiedHunk, SplitHunk {

    /**
     * How wide the file is drawn: its bar is tinted, and its stops but those of a line are, as wide.
     */
    int width();

    /**
     * How the threads on the file as a whole, and those that cannot be placed, are drawn.
     */
    Blocks blocks();

    /**
     * Where the markers of the file go: of the lines it leaves out (ADR 77), and of the hunks filters hide (ADR 144).
     */
    FileSections.MarkerAt markerAt();

    /**
     * What marks where hunk {@code at} starts when no lines are left out before it: a rule, if the hunk needs one.
     */
    List<Marked> heading(int at);

    /**
     * The lines still to be drawn of hunk {@code at}, after its heading: those of its lines, with the threads on them.
     * The threads after its last line are followed by a blank line if the hunk is {@code followed} by anything in its
     * file.
     */
    List<Pending> hunk(int at, boolean followed);

    /**
     * {@code line}, a line of the file, as this layout places it.
     */
    Marked placed(Marked line);
}
