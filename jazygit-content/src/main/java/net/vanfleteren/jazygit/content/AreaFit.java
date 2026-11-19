package net.vanfleteren.jazygit.content;

import static net.vanfleteren.jazygit.content.ContentText.window;
import static net.vanfleteren.jazygit.content.ContentText.windowed;

import dev.tamboui.text.Text;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.content.AreaState.Columns;
import net.vanfleteren.jazygit.content.AreaState.GoToLine;
import net.vanfleteren.jazygit.content.AreaState.Search;
import net.vanfleteren.jazygit.content.AreaState.Sent;
import net.vanfleteren.jazygit.content.AreaState.Wanted;
import net.vanfleteren.jazygit.content.diff.DiffCursor;
import net.vanfleteren.jazygit.content.diff.RichDiff;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.DiffStops;
import net.vanfleteren.jazygit.state.diff.Scrolled;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.state.view.ContentSearch;
import net.vanfleteren.jazygit.ui.content.Body;
import net.vanfleteren.jazygit.ui.content.Drawn;
import net.vanfleteren.jazygit.ui.content.Shown;
import net.vanfleteren.jazygit.ui.i18n.Messages;
import net.vanfleteren.jazygit.ui.text.Rows;
import net.vanfleteren.jazygit.ui.theme.Theme;

/**
 * How a frame shows an area: from the area as it was before and what the frame shows in it, the area after and what
 * its text area draws. A function, so a frame drawn in another size works it out again from where the first left it.
 */
final class AreaFit {

    private AreaFit() {}

    /**
     * The area {@code before}, as {@code showing} shows it in a text area that showed {@code view} when it last drew:
     * its lines fitted to the size it was last drawn in, which the text area only learns while it draws (ADR 80).
     */
    static Fitted fit(AreaState before, Showing showing, Viewport view) {
        Scrolled known = showing.model().scrolled(before.area(), showing.content());
        Option<Sent> sent = before.sent().filter(given -> given.known().equals(known));
        Scrolled told = sent.map(Sent::told).getOrElse(known);
        Restyled restyled = restyled(before, showing, told, view);
        Option<Integer> shift = shift(before, restyled, told);
        // Shown in another order, or as another version of the file, the line of the cursor stays where it was on the
        // screen: the view moves by as much as it does, which the model learns with the next move (ADR 83, 102).
        Scrolled at = shift.map(by -> new Scrolled(
                        told.content(),
                        Math.max(0, told.row() + by),
                        told.column(),
                        told.cursor(),
                        told.mark(),
                        told.found()))
                .getOrElse(told);
        Styled current = restyled.current();
        Option<DiffCursor> cursor = DiffCursor.of(
                current.stops(),
                restyled.near(),
                current.lines().size(),
                view.height(),
                at.row(),
                at.cursor(),
                at.mark(),
                restyled.side());
        AreaState shown = shown(before, showing, known, restyled)
                .withSent(shift.isDefined() ? Option.of(new Sent(known, at)) : sent)
                .withCursor(cursor);
        return drawn(waited(shown, restyled), showing, view, restyled, at);
    }

    /**
     * The body of the area drawn as {@code showing} has it, or as it was drawn before when that still holds.
     */
    private static Restyled restyled(AreaState before, Showing showing, Scrolled told, Viewport view) {
        ThemeName theme = showing.model().preferences().theme();
        ScrolledContent content = showing.content();
        ScrolledContent was = before.content();
        Option<Styled> sameChanges =
                before.styled().filter(drawn -> drawn.theme() == theme && was.sameChanges(content));
        // The new file's side in a diff shown anew, else where it was. Kept by the area, as it is not where the cursor
        // is but where it goes.
        ReviewThread.Side side = before.cursor()
                .filter(ignored -> was.equals(content))
                .map(DiffCursor::side)
                .getOrElse(ReviewThread.Side.RIGHT);
        Body body = showing.shown().body();
        // A version rendered, or the builds, are drawn as wide as the area, which it learns while it draws (ADR 80,
        // 103, 131).
        int width = view.width() > 0 ? view.width() : RichDiff.RENDERED_WIDTH;
        Option<Styled> last = before.styled()
                .filter(drawn -> drawn.body().equals(body)
                        && drawn.theme() == theme
                        && (!body.drawnToWidth() || drawn.width() == width));
        int near = before.cursor().map(DiffCursor::stop).getOrElse(0);
        // The hunk a range is open in is shown as git gives it, so a range holds the lines it shows (ADR 83). A new
        // body has its hunk looked up in its diff, so it is drawn once, in the order it is shown in.
        Option<DiffStops.HunkAt> asGiven = told.mark()
                .flatMap(mark -> last.map(drawn -> drawn.hunk(mark, near)).getOrElse(() -> body.markedHunk(mark)));
        // The matches are found once per body and query, not per frame (ADR 126).
        Styled current = last.filter(drawn -> drawn.asGiven().equals(asGiven))
                .getOrElse(() -> Styled.of(body, theme, asGiven, showing.style().drawn(body, asGiven, width), width))
                .searched(ContentSearch.query(showing.model().contentSearch()));
        return new Restyled(last, sameChanges, current, near, side);
    }

    /**
     * How many lines the view moves so the line of the cursor stays where it was on the screen, if it moves: a line
     * the cursor was moved to that was not drawn before takes the place of the line the cursor was drawn on.
     */
    private static Option<Integer> shift(AreaState before, Restyled restyled, Scrolled told) {
        int near = restyled.near();
        Styled current = restyled.current();
        Option<Anchor> drawnOn = before.cursor().map(DiffCursor::anchor);
        return restyled.last()
                .orElse(restyled.sameChanges())
                .filter(drawn -> drawn != current)
                .flatMap(drawn -> told.cursor()
                        .flatMap(cursorAt -> drawn.row(cursorAt, near)
                                .orElse(() -> drawnOn.flatMap(on -> drawn.row(on, near)))
                                .flatMap(was -> current.row(cursorAt, near).map(is -> is - was))))
                .filter(by -> by != 0);
    }

    /**
     * The area {@code before} as {@code showing} shows it, where the model has it {@code known}: the lines to type a
     * search or a line number in as the model has them, and what the title said kept only while it stays the same.
     */
    private static AreaState shown(AreaState before, Showing showing, Scrolled known, Restyled restyled) {
        Model model = showing.model();
        ScrolledContent content = showing.content();
        boolean focused = showing.focused();
        Option<String> typing =
                model.contentSearch().filter(ContentSearch::typing).map(ContentSearch::text);
        // The line to type a line number in shows above the area with the focus, unless the line of a search does.
        Option<String> number = model.lineNumber().filter(ignored -> typing.isEmpty());
        // What the title said of the search lasts until the next key in the area: the area losing the focus, or
        // showing another body, is past that too (ADR 126).
        Option<String> notice =
                before.notice().filter(ignored -> focused && restyled.last().isDefined());
        // A line asked for that a marker left out is waited for only while the area keeps the focus and shows what
        // it was asked in: reading the file whole does not change that, another file does (ADR 140).
        Option<Wanted> wanted = before.line()
                .wanted()
                .filter(asked -> focused && asked.content().equals(content));
        return before.shown(
                        known,
                        focused,
                        showing.shown().body().cursorKeys(),
                        model.repoView().version(before.area(), content))
                .withSearch(new Search(typing, before.search().current()))
                .withLine(new GoToLine(number, wanted))
                .withNotice(notice);
    }

    /**
     * {@code shown}, still waiting for a line asked for that a marker left out until it shows, or what showed instead
     * says it is not there, as past the end of the file, which is only known once the file is read whole (ADR 140).
     */
    private static AreaState waited(AreaState shown, Restyled restyled) {
        return restyled.last().isEmpty() ? AreaLines.arrived(shown, restyled.current()) : shown;
    }

    /**
     * {@code shown} drawn in a text area that shows {@code view}, scrolled {@code at}: only the lines that show are
     * kept drawn for the frames after ({@link Rows}), the line of the cursor tinted in the area with the focus, and the
     * matches of a search.
     */
    private static Fitted drawn(AreaState shown, Showing showing, Viewport view, Restyled restyled, Scrolled at) {
        Styled current = restyled.current();
        int furthest = Math.max(0, current.widest() - view.width());
        Columns columns = new Columns(Math.clamp(at.column(), 0, furthest), furthest);
        int row = shown.cursor().map(DiffCursor::row).getOrElse(at.row());
        ContentText.Window window = window(current.lines().size(), row, view.height());
        Rows kept = current.lines().keeping(window.top(), window.bottom());
        Rows tinted = shown.cursor()
                .filter(ignored -> showing.focused())
                .map(on -> on.tinted(kept, showing.styles().of(Theme.Classes.DIFF_CURSOR)))
                .getOrElse(kept);
        Option<Integer> match = current.hits()
                .flatMap(hits -> at.found().flatMap(found -> current.match(hits, found, restyled.near())));
        Rows lines = current.hits()
                .map(hits -> hits.tinted(
                        tinted,
                        window.top(),
                        window.bottom(),
                        match,
                        showing.styles().of(Theme.Classes.SEARCH_MATCH),
                        showing.styles().of(Theme.Classes.SEARCH_CURRENT)))
                .getOrElse(tinted);
        AreaState after = shown.withStyled(Option.of(current.withLines(kept)))
                .withColumns(columns)
                .withMatch(match);
        Drawn drawn = current.split();
        Text text = windowed(
                lines,
                columns.column(),
                row,
                view.height(),
                view.width(),
                current::filling,
                drawn::halvesOf,
                drawn::keptAt);
        String title = showing.shown().title()
                + drawn.note()
                        .map(says -> Messages.get("content.search.notice", says))
                        .getOrElse("")
                + AreaSearch.title(after, current.hits());
        return new Fitted(after, new Drawing(text, title, current.lines().size(), row));
    }

    /**
     * What a frame shows in an area.
     *
     * @param model   the model drawn
     * @param focused whether the area has the focus
     * @param shown   what the area shows, and its title
     * @param style   how its body is drawn
     * @param styles  the styles of the theme, for the tints of the cursor and of the matches of a search
     */
    record Showing(Model model, boolean focused, Shown.Area shown, ScrollArea.Styler style, Theme.SpanStyles styles) {

        ScrolledContent content() {
            return shown.content();
        }
    }

    /**
     * An area fitted to a frame: the area after it, and what its text area draws.
     */
    record Fitted(AreaState after, Drawing drawing) {}

    /**
     * What the text area of an area draws: {@code text}, under {@code title}, of {@code lines} lines, scrolled down to
     * {@code row}.
     */
    record Drawing(Text text, String title, int lines, int row) {}

    /**
     * The body of the area drawn as {@code showing} has it, or as it was drawn before when that still holds.
     *
     * @param last        what was drawn before, if it still shows the same
     * @param sameChanges what was drawn before, for the same changes drawn anew, as when a version of the file shows
     *                    in place of its diff (ADR 102): the line of the cursor stays where it was on the screen then too
     * @param current     the body drawn now, with the matches of the search in it
     * @param near        the stop the cursor was on, which the stops of the cursor and the range are looked for from
     * @param side        the side of the split view the cursor keeps to while it is on no half (ADR 139)
     */
    private record Restyled(
            Option<Styled> last, Option<Styled> sameChanges, Styled current, int near, ReviewThread.Side side) {}
}
