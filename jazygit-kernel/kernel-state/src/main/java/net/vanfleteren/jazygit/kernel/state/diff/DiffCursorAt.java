package net.vanfleteren.jazygit.kernel.state.diff;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Where the cursor of the diff of a pull request's files is, which the keys of that diff act on (ADR 60), with where
 * the keys that move it take it: the content panel works those out, as only it knows the lines of the diff and how
 * high it is shown.
 *
 * @param area     the id of the area of the content panel the diff is shown in
 * @param at       where the area is scrolled to, and its cursor and range
 * @param cursor   the stop the cursor is on
 * @param next     where {@code n} takes the cursor: to the next match while a search is kept (ADR 126), else to the
 *                 next thread, if there is one
 * @param previous where {@code N} takes it: to the match, or else the thread, before
 * @param marked   where {@code v} takes it: a range started on its line, or dropped; none when it is not on a line
 * @param split    whether the cursor is on a half of a row of the split view (ADR 139), where a range holds the lines
 *                 of its side only: as the diff was drawn, which is unified where the split view does not fit, or
 *                 for a file it cannot pair the lines of, whatever the style asked for
 * @param goToLine what {@code :} sends: the line to type a line number in opened, where the area shows lines of a file
 *                 to go to; none elsewhere, as on a binary file (ADR 140)
 * @param opens    whether the cursor is on a line of a version shown blamed whose commit Enter opens the files of
 *                 (ADR 147): blamed on a commit, not the one shown
 */
public record DiffCursorAt(
        AreaId area,
        Scrolled at,
        Anchor cursor,
        Option<Msg> next,
        Option<Msg> previous,
        Option<Msg> marked,
        boolean split,
        Option<Msg> goToLine,
        boolean opens) {

    /**
     * Where the cursor is, on no line whose commit Enter opens.
     */
    public DiffCursorAt(
            AreaId area,
            Scrolled at,
            Anchor cursor,
            Option<Msg> next,
            Option<Msg> previous,
            Option<Msg> marked,
            boolean split,
            Option<Msg> goToLine) {
        this(area, at, cursor, next, previous, marked, split, goToLine, false);
    }

    /**
     * Where the cursor is, in what has no line of a file to go to by its number.
     */
    public DiffCursorAt(
            AreaId area,
            Scrolled at,
            Anchor cursor,
            Option<Msg> next,
            Option<Msg> previous,
            Option<Msg> marked,
            boolean split) {
        this(area, at, cursor, next, previous, marked, split, Option.none());
    }

    /**
     * Where the cursor of a diff drawn unified is, in what has no line of a file to go to by its number.
     */
    public DiffCursorAt(
            AreaId area, Scrolled at, Anchor cursor, Option<Msg> next, Option<Msg> previous, Option<Msg> marked) {
        this(area, at, cursor, next, previous, marked, false);
    }

    /**
     * The other end of the range, while one is open.
     */
    public Option<Anchor> mark() {
        return at.mark();
    }
}
