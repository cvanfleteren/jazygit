package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;

/**
 * Where the cursor of the diff of a pull request's files is, which the keys of that diff act on (ADR 60), with where
 * the keys that move it take it: the content panel works those out, as only it knows the lines of the diff and how
 * high it is shown.
 *
 * @param area     the id of the area of the content panel the diff is shown in
 * @param at       where the area is scrolled to, and its cursor and range
 * @param cursor   the stop the cursor is on
 * @param next     where {@code n} takes the cursor: to the next thread, if there is one
 * @param previous where {@code N} takes it: to the thread before
 * @param marked   where {@code v} takes it: a range started on its line, or dropped; none when it is not on a line
 */
public record DiffCursorAt(
        String area, Scrolled at, Anchor cursor, Option<Msg> next, Option<Msg> previous, Option<Msg> marked) {

    /**
     * The other end of the range, while one is open.
     */
    public Option<Anchor> mark() {
        return at.mark();
    }
}
