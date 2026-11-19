package net.vanfleteren.jazygit.content;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.content.diff.DiffCursor;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.FileVersion;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.state.diff.Scrolled;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.state.diff.Version;
import net.vanfleteren.jazygit.ui.keys.Keymap;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * What a {@link ScrollArea} knows of itself between frames, as last shown: a value {@link AreaFit} works out anew each
 * frame from the one before, and the keys and the mouse turn into another ({@link Reaction}). The text area that draws
 * it keeps only the layout of the text, and how far down it is scrolled.
 *
 * @param area    the area it is
 * @param styled  the body last shown, the theme it was styled in and its text: a frame that shows the same, like one
 *                drawn only to turn a spinner, or a move of the cursor, does not style it again
 * @param cursor  the cursor of the rich diff shown, as last drawn: what the keys and the wheel move (ADR 60)
 * @param shownAt where the model had the area when it was last shown
 * @param sent    where the area told the model it went from there: until the model has it, a frame drawn meanwhile
 *                shows that, so a key pressed right after another acts on where that one took the cursor (ADR 60)
 * @param columns how far the area is scrolled sideways, and how far it can be
 * @param drawnIn the size the area was last drawn in, which its lines are fitted to; none before it is first drawn (ADR
 *                80)
 * @param search  the search of the content panel as last shown (ADR 126)
 * @param line    going to a line by its number, as last shown (ADR 140)
 * @param notice  what the title says until the next key: that n went past the last match, or could not go to one, or
 *                that a line is not there (ADR 126, 140)
 * @param focused whether the area had the focus when it was last shown
 * @param keys    the keys of the cursor on what it shows: those that comment on the diff of a pull request's files
 *                (ADR 60), else those of a diff
 * @param version the version of its file the area shows, if it shows one: on a line of it blamed, Enter opens its
 *                commit (ADR 147)
 */
record AreaState(
        AreaId area,
        Option<Styled> styled,
        Option<DiffCursor> cursor,
        Scrolled shownAt,
        Option<Sent> sent,
        Columns columns,
        Option<DrawnIn> drawnIn,
        Search search,
        GoToLine line,
        Option<String> notice,
        boolean focused,
        Keymap.Scope<DiffCursorAt> keys,
        Option<Version> version) {

    /**
     * The area before it is first shown.
     */
    static AreaState initial(AreaId area) {
        return new AreaState(
                area,
                Option.none(),
                Option.none(),
                Scrolled.top(new ScrolledContent.Diff(DiffText.of(""))),
                Option.none(),
                new Columns(0, 0),
                Option.none(),
                new Search(Option.none(), Option.none()),
                new GoToLine(Option.none(), Option.none()),
                Option.none(),
                false,
                Scopes.DIFF,
                Option.none());
    }

    /**
     * What the area shows.
     */
    ScrolledContent content() {
        return shownAt.content();
    }

    /**
     * Whether the area shows the original of a file whole, whose lines are numbered in the old file (ADR 102, 140).
     */
    boolean original() {
        return version.flatMap(Version::showing).contains(FileVersion.ORIGINAL);
    }

    /**
     * Whether a search is kept in what the area shows (ADR 126).
     */
    boolean searching() {
        return styled.exists(shown -> shown.hits().isDefined());
    }

    /**
     * Remembers that the model was told the area is now at {@code to}.
     */
    AreaState told(Scrolled to) {
        return withSent(Option.of(new Sent(sent.map(Sent::known).getOrElse(shownAt), to)));
    }

    AreaState withStyled(Option<Styled> styled) {
        return new AreaState(
                area, styled, cursor, shownAt, sent, columns, drawnIn, search, line, notice, focused, keys, version);
    }

    AreaState withCursor(Option<DiffCursor> cursor) {
        return new AreaState(
                area, styled, cursor, shownAt, sent, columns, drawnIn, search, line, notice, focused, keys, version);
    }

    AreaState withSent(Option<Sent> sent) {
        return new AreaState(
                area, styled, cursor, shownAt, sent, columns, drawnIn, search, line, notice, focused, keys, version);
    }

    AreaState withColumn(int column) {
        return withColumns(new Columns(column, columns.furthest()));
    }

    AreaState withColumns(Columns columns) {
        return new AreaState(
                area, styled, cursor, shownAt, sent, columns, drawnIn, search, line, notice, focused, keys, version);
    }

    AreaState withDrawnIn(Option<DrawnIn> drawnIn) {
        return new AreaState(
                area, styled, cursor, shownAt, sent, columns, drawnIn, search, line, notice, focused, keys, version);
    }

    AreaState withSearch(Search search) {
        return new AreaState(
                area, styled, cursor, shownAt, sent, columns, drawnIn, search, line, notice, focused, keys, version);
    }

    AreaState withLine(GoToLine line) {
        return new AreaState(
                area, styled, cursor, shownAt, sent, columns, drawnIn, search, line, notice, focused, keys, version);
    }

    AreaState withNotice(Option<String> notice) {
        return new AreaState(
                area, styled, cursor, shownAt, sent, columns, drawnIn, search, line, notice, focused, keys, version);
    }

    /**
     * The area as a frame shows it: what is shown {@code at}, with the focus or not, and what of it.
     */
    AreaState shown(Scrolled at, boolean focused, Keymap.Scope<DiffCursorAt> keys, Option<Version> version) {
        return new AreaState(
                area, styled, cursor, at, sent, columns, drawnIn, search, line, notice, focused, keys, version);
    }

    /**
     * The match of the search the area went to, if one of them.
     */
    AreaState withMatch(Option<Integer> current) {
        return withSearch(new Search(search.typing(), current));
    }

    /**
     * Where the model had the area when it was last shown, and where the area told it it went from there (ADR 60).
     *
     * @param known where the model had the area
     * @param told  where the area told the model it went
     */
    record Sent(Scrolled known, Scrolled told) {}

    /**
     * A line asked for that a marker leaves out, while the area waits for it to show (ADR 140).
     *
     * @param content what the area showed when it was asked for
     * @param line    the number of the line asked for
     */
    record Wanted(ScrolledContent content, int line) {}

    /**
     * How far the area is scrolled sideways, as last drawn, and how far it can be. TamboUI's text area keeps a column
     * it never draws at, so the area draws its lines with as many columns cut off their start itself.
     *
     * @param column   the first column shown
     * @param furthest the column it can be scrolled to at most: how much wider than the area its widest line is
     */
    record Columns(int column, int furthest) {}

    /**
     * The size an area was drawn in.
     */
    record DrawnIn(int width, int height) {}

    /**
     * The search of the content panel as last shown (ADR 126).
     *
     * @param typing  the text of its line while that is open
     * @param current which of the matches in the body shown the area went to, if one of them
     */
    record Search(Option<String> typing, Option<Integer> current) {}

    /**
     * Going to a line by its number, as last shown (ADR 140).
     *
     * @param number the text of the line to type the number in, while it is open
     * @param wanted the line asked for that a marker leaves out, while the area waits for it to show
     */
    record GoToLine(Option<String> number, Option<Wanted> wanted) {

        GoToLine withNumber(Option<String> number) {
            return new GoToLine(number, wanted);
        }

        GoToLine withWanted(Option<Wanted> wanted) {
            return new GoToLine(number, wanted);
        }
    }
}
