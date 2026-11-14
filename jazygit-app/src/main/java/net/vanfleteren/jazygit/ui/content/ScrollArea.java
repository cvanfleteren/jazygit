package net.vanfleteren.jazygit.ui.content;

import static dev.tamboui.toolkit.Toolkit.*;
import static net.vanfleteren.jazygit.ui.content.ContentText.markedHunk;
import static net.vanfleteren.jazygit.ui.content.ContentText.rendering;
import static net.vanfleteren.jazygit.ui.content.ContentText.window;
import static net.vanfleteren.jazygit.ui.content.ContentText.windowed;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.RichTextState;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.elements.RichTextAreaElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.bindings.Actions;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.tui.event.PasteEvent;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarOrientation;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.state.diff.DiffStops;
import net.vanfleteren.jazygit.state.diff.Found;
import net.vanfleteren.jazygit.state.diff.Scrolled;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.state.view.ContentSearch;
import net.vanfleteren.jazygit.state.view.ContentSearchMsg;
import net.vanfleteren.jazygit.state.view.FileStepMsg;
import net.vanfleteren.jazygit.state.view.ViewMsg;
import net.vanfleteren.jazygit.ui.content.ContentView.Body;
import net.vanfleteren.jazygit.ui.content.ContentView.Shown;
import net.vanfleteren.jazygit.ui.diff.DiffCursor;
import net.vanfleteren.jazygit.ui.diff.RichDiff;
import net.vanfleteren.jazygit.ui.text.Rows;
import net.vanfleteren.jazygit.ui.theme.Theme;
import net.vanfleteren.jazygit.ui.widgets.BorderBar;
import net.vanfleteren.jazygit.ui.widgets.FilterLine;
import net.vanfleteren.jazygit.ui.widgets.PasteTarget;

/**
 * A scrollable area of the content panel. It shows what it is given at the position the model has for that, and tells
 * where the user scrolled it to; the element only keeps the layout of the text.
 */
public final class ScrollArea {

    private static final int WHEEL_LINES = 3;
    // The keys that move the cursor of a rich diff by a stop besides the arrows, as the lists of the panes are
    // moved (ADR 60).
    private static final char UP = 'k';
    private static final char DOWN = 'j';
    // The keys that scroll an area sideways besides the arrows, to the end of a long line or a wide code block.
    private static final char LEFT = 'h';
    private static final char RIGHT = 'l';
    // The keys that scroll an area all the way sideways, besides Shift with the arrows.
    private static final char ALL_LEFT = 'H';
    private static final char ALL_RIGHT = 'L';
    // The keys that go to the next and the previous match while a search is kept (ADR 126).
    private static final char NEXT_MATCH = 'n';
    private static final char PREVIOUS_MATCH = 'N';
    private static final Keymap KEYMAP = Keymap.standard();

    private final ContentArea area;
    private final String id;
    private final RichTextAreaElement element;
    private final BooleanSupplier leaveScrollArea;
    private final Consumer<String> focus;
    private final Consumer<Msg> dispatch;
    // The body last shown, the theme it was styled in and its text: a frame that shows the same, like one drawn
    // only to turn a spinner, or a move of the cursor, does not style it again.
    private Option<Styled> styled = Option.none();
    // The cursor of the rich diff shown, as last drawn: what the keys and the wheel move (ADR 60).
    private Option<DiffCursor> cursor = Option.none();
    // Where the model had the area when it was last shown, and where the area told it it went from there: until
    // the model has it, a frame drawn meanwhile shows the latter, so a key pressed right after another acts on
    // where that one took the cursor (ADR 60).
    private Scrolled shownAt = Scrolled.top(new ScrolledContent.Diff(DiffText.of("")));
    private Option<Tuple2<Scrolled, Scrolled>> sent = Option.none();
    // Whether it shows the diff of a pull request's files, whose keys comment on it (ADR 60).
    private boolean review = false;
    // How far the area is scrolled sideways, as last drawn, and how far it can be. TamboUI's text area keeps a
    // column it never draws at, so the area draws its lines with as many columns cut off their start itself, and
    // shows where it is with TamboUI's scrollbar on its bottom border, as it shows how far down on its right one.
    private int column = 0;
    private int columns = 0;
    // The size the area was last drawn in, which its lines are fitted to; none before it is first drawn (ADR 80).
    private int drawnWidth = -1;
    private int drawnHeight = -1;
    // The search of the content panel as last shown (ADR 126): the text of its line while that is open, and which
    // of the matches in the body shown the area went to, if one of them.
    private final FilterLine searchLine = new FilterLine();
    private Option<String> typing = Option.none();
    private Option<Integer> currentMatch = Option.none();
    // What the title says until the next key: that n went past the last match, or could not go to one (ADR 126).
    private Option<String> notice = Option.none();

    public ScrollArea(
            ContentArea area, BooleanSupplier leaveScrollArea, Consumer<String> focus, Consumer<Msg> dispatch) {
        this.area = area;
        this.id = area.id().value();
        this.leaveScrollArea = leaveScrollArea;
        this.focus = focus;
        this.dispatch = dispatch;
        // Without TamboUI's scrollbar, which takes a column inside the border: the area draws its own on the border.
        element = richTextArea().id(id).fill();
    }

    /**
     * Makes the area part of the Tab cycle while {@code inContent}, so Tab goes from one area to the
     * other then, but from the last pane back to the first otherwise.
     */
    public void tabbable(boolean inContent) {
        element.focusable(inContent);
    }

    /**
     * The area as {@code shown}, its body as {@code style} styles it, where the model has it scrolled to, its lines
     * fitted to the size it is drawn in (ADR 80).
     */
    public Element show(Model model, String focusedId, Shown.Area shown, Styler style, Theme.SpanStyles styles) {
        Element fitted = new Fitted(
                fit(model, focusedId, shown, style, styles), () -> fit(model, focusedId, shown, style, styles));
        // The line to type the query of a search in, above the area that has the focus, which keeps it (ADR 126).
        return typing.filter(ignored -> id.equals(focusedId))
                .<Element>map(text ->
                        new PasteTarget(column(searchLine.field(text), fitted).fill(), id, this::pasted))
                .getOrElse(fitted);
    }

    private void pasted(PasteEvent event) {
        typing.forEach(text -> dispatch.accept(new ContentSearchMsg.Edited(searchLine.paste(text, event))));
    }

    /**
     * The text area as {@code shown}, as {@link #show} says, its lines fitted to the size the area was last drawn
     * in, which the text area only learns while it draws.
     */
    private RichTextAreaElement fit(
            Model model, String focusedId, Shown.Area shown, Styler style, Theme.SpanStyles styles) {
        ThemeName theme = model.preferences().theme();
        ScrolledContent content = shown.content();
        Scrolled known = model.scrolled(area.id(), content);
        sent = sent.filter(told -> told._1().equals(known));
        Scrolled told = sent.map(Tuple2::_2).getOrElse(known);
        // What was drawn before, for the same changes drawn anew, as when a version of the file shows in place of
        // its diff (ADR 102): the line of the cursor stays where it was on the screen then too.
        Option<Styled> sameChanges = styled.filter(
                before -> before.theme() == theme && shownAt.content().sameChanges(content));
        shownAt = known;
        Body body = shown.body();
        // A version rendered is drawn as wide as the area, which it learns while it draws (ADR 80, 103).
        int width = element.state().viewportWidth() > 0 ? element.state().viewportWidth() : RichDiff.RENDERED_WIDTH;
        Option<Styled> last = styled.filter(before ->
                before.body().equals(body) && before.theme() == theme && (!rendering(body) || before.width() == width));
        // The hunk a range is open in is shown as git gives it, so a range holds the lines it shows (ADR 83). A new
        // body has its hunk looked up in its diff, so it is drawn once, in the order it is shown in.
        // The stop the cursor was on, which the stops of the cursor and the range are looked for from.
        int near = cursor.map(DiffCursor::stop).getOrElse(0);
        Option<DiffStops.HunkAt> asGiven = told.mark()
                .flatMap(mark -> last.map(before -> before.hunk(mark, near)).getOrElse(() -> markedHunk(body, mark)));
        Option<String> query = ContentSearch.query(model.contentSearch());
        typing = model.contentSearch().filter(ContentSearch::typing).map(ContentSearch::text);
        // The matches are found once per body and query, not per frame (ADR 126).
        Styled current = last.filter(before -> before.asGiven().equals(asGiven))
                .getOrElse(() -> Styled.of(body, theme, asGiven, style.drawn(body, asGiven, width), width))
                .searched(query);
        styled = Option.of(current);
        // Shown in another order, or as another version of the file, the line of the cursor stays where it was on
        // the screen: the view moves by as much as it does, which the model learns with the next move (ADR 83,
        // 102). A line the cursor was moved to that was not drawn before takes the place of the line the cursor
        // was drawn on.
        Option<Anchor> drawnOn = cursor.map(DiffCursor::anchor);
        Scrolled at = last.orElse(sameChanges)
                .filter(before -> before != current)
                .flatMap(before -> told.cursor()
                        .flatMap(cursorAt -> before.row(cursorAt, near)
                                .orElse(() -> drawnOn.flatMap(on -> before.row(on, near)))
                                .flatMap(was -> current.row(cursorAt, near).map(is -> is - was))))
                .filter(shift -> shift != 0)
                .map(shift -> {
                    Scrolled shifted = new Scrolled(
                            told.content(),
                            Math.max(0, told.row() + shift),
                            told.column(),
                            told.cursor(),
                            told.mark(),
                            told.found());
                    sent = Option.of(Tuple.of(known, shifted));
                    return shifted;
                })
                .getOrElse(told);
        RichTextState state = element.state();
        cursor = DiffCursor.of(
                current.stops(),
                near,
                current.lines().size(),
                state.viewportHeight(),
                at.row(),
                at.cursor(),
                at.mark());
        review = shown.body() instanceof Body.Diff(var diff, var look, var remarks) && remarks.isDefined();
        boolean focused = id.equals(focusedId);
        // What the title said of the search lasts until the next key in the area: the area losing the focus, or
        // showing another body, is past that too (ADR 126).
        if (!focused || last.isEmpty()) {
            notice = Option.none();
        }
        // The size the element was last drawn in, which a frame in another size draws it in first (ADR 80).
        columns = Math.max(0, current.widest() - state.viewportWidth());
        column = Math.clamp(at.column(), 0, columns);
        int row = cursor.map(DiffCursor::row).getOrElse(at.row());
        // The lines that show are kept drawn for the frames after, and only they ({@link Rows}).
        Tuple2<Integer, Integer> window = window(current.lines().size(), row, state.viewportHeight());
        Rows kept = current.lines().keeping(window._1(), window._2());
        styled = Option.of(current.withLines(kept));
        Rows tinted = cursor.filter(ignored -> focused)
                .map(on -> on.tinted(kept, styles.of(Theme.Classes.DIFF_CURSOR)))
                .getOrElse(kept);
        currentMatch = current.hits().flatMap(hits -> at.found().flatMap(found -> current.match(hits, found, near)));
        Rows lines = current.hits()
                .map(hits -> hits.tinted(
                        tinted,
                        window._1(),
                        window._2(),
                        currentMatch,
                        styles.of(Theme.Classes.SEARCH_MATCH),
                        styles.of(Theme.Classes.SEARCH_CURRENT)))
                .getOrElse(tinted);
        element.text(windowed(lines, column, row, state.viewportHeight(), state.viewportWidth(), current::filling))
                .title(shown.title() + searchTitle(current.hits()));
        // The state clamps a scroll to the lines it was last drawn with: a longer body, as a version of the file
        // in place of its diff, would show only the blank lines around where it was cut off (ADR 78, 102).
        state.setContentHeight(current.lines().size());
        state.scrollToTop();
        state.scrollDown(row);
        return element.onKeyEvent(event -> keys(event, focused, content)).onMouseEvent(event -> mouse(event, content));
    }

    /**
     * What {@code event} does: Left leaves the area as Escape does once it is scrolled all the way left, as Right in
     * its pane moves into it. Left held down stops there: only pressing it again leaves (ADR 118).
     */
    private EventResult keys(KeyEvent event, boolean focused, ScrolledContent content) {
        if (focused && typing.isDefined()) {
            return typed(typing.get(), event, content);
        }
        if (focused) {
            notice = Option.none();
        }
        // Only the arrow leaves: h scrolls, as j and k move, without going to another panel.
        boolean atLeftEdge = focused && event.matches(Actions.MOVE_LEFT) && !event.hasShift() && column == 0;
        if (atLeftEdge && event.isRepeat()) {
            return EventResult.HANDLED;
        }
        boolean leaving = event.isCancel() || atLeftEdge;
        // Escape, and Left where it leaves as Escape does, drop an open range before they leave the area (ADR 60).
        return cursor.filter(on -> leaving && focused && on.mark().isDefined())
                .map(ranged -> moved(ranged.marked().get(), content))
                // Then a search kept, before the area is left (ADR 126).
                .orElse(() -> Option.when(
                        focused
                                && event.isCancel()
                                && styled.exists(shown -> shown.hits().isDefined()),
                        () -> sending(new ContentSearchMsg.Cleared())))
                // Key handlers also see keys typed elsewhere; the callback checks that an area has focus.
                .orElse(() -> Option.when(leaving && leaveScrollArea.getAsBoolean(), EventResult.HANDLED))
                .orElse(() -> Option.when(!focused, EventResult.UNHANDLED))
                .orElse(() -> searchStep(event, content))
                .orElse(() -> cursor.flatMap(on -> move(on, event)).map(to -> moved(to, content)))
                .orElse(() -> cursor.flatMap(on -> KEYMAP.handle(
                                review ? Scopes.PULL_REQUEST_DIFF : Scopes.DIFF, event, Option.of(at(on, content))))
                        .map(this::reviewed))
                // Without a cursor, as in the plain style, only the keys that move to another file act (ADR 117).
                .orElse(() -> Option.when(cursor.isEmpty(), () -> KEYMAP.handle(Scopes.DIFF, event, Option.none()))
                        .flatMap(msg -> msg)
                        .filter(msg -> msg instanceof FileStepMsg || msg instanceof ContentSearchMsg)
                        .map(this::reviewed))
                .getOrElse(() -> scroll(event) ? scrolled(content) : EventResult.UNHANDLED);
    }

    /**
     * What {@code event} does while the line of the search is open with {@code text} (ADR 126): the keys that edit it
     * do, Escape closes it, Enter closes it and goes to the first match from the cursor, or from the top of the screen
     * without one. The other keys do nothing.
     */
    private EventResult typed(String text, KeyEvent event, ScrolledContent content) {
        if (event.isCancel()) {
            return sending(new ContentSearchMsg.Cancelled());
        }
        if (event.isConfirm()) {
            dispatch.accept(new ContentSearchMsg.Submitted());
            currentMatch = Option.none();
            return styled.map(shown -> shown.searched(Option.when(!text.isEmpty(), text)))
                    .peek(searched -> styled = Option.of(searched))
                    .flatMap(Styled::hits)
                    .filter(hits -> !hits.isEmpty())
                    .map(hits -> goTo(hits, hits.forwardFrom(position()), content))
                    .getOrElse(EventResult.HANDLED);
        }
        searchLine.edit(text, event).map(ContentSearchMsg.Edited::new).forEach(dispatch);
        return EventResult.HANDLED;
    }

    private EventResult sending(Msg msg) {
        dispatch.accept(msg);
        return EventResult.HANDLED;
    }

    /**
     * Where {@code n} and {@code N} go while a search is kept (ADR 126), if {@code event} is one of them: from the
     * match gone to, else from the cursor, or the top of the screen without one; nowhere when nothing matches.
     */
    private Option<EventResult> searchStep(KeyEvent event, ScrolledContent content) {
        boolean forward = event.isChar(NEXT_MATCH);
        return styled.flatMap(Styled::hits)
                .filter(ignored -> forward || event.isChar(PREVIOUS_MATCH))
                .map(hits -> hits.isEmpty()
                        ? EventResult.HANDLED
                        : goTo(
                                hits,
                                currentMatch
                                        .map(at -> hits.from(at, forward))
                                        .getOrElse(() ->
                                                forward ? hits.forwardFrom(position()) : hits.backwardFrom(position())),
                                content));
    }

    /**
     * The line a search goes on from when no match is current: the first of the stop of the cursor, else the first
     * on the screen.
     */
    private int position() {
        return cursor.map(DiffCursor::stopStart).getOrElse(() -> element.state().scrollRow());
    }

    /**
     * Goes to the match {@code step} says of {@code hits}: the cursor to its stop, or the view scrolled to it, and
     * sideways until it shows, telling the model in one message. While a range is open, a match outside its hunk is
     * refused, and the title says so.
     */
    private EventResult goTo(SearchHits hits, SearchHits.Step step, ScrolledContent content) {
        SearchHits.Hit hit = hits.get(step.index());
        Option<String> wrapped = Option.when(step.wrapped(), () -> Messages.get("content.search.wrapped"));
        RichTextState state = element.state();
        if (cursor.isDefined()) {
            Option<DiffCursor> to = cursor.get().toLine(hit.row());
            if (to.isEmpty()) {
                notice = Option.of(Messages.get("content.search.outsideRange"));
                return EventResult.HANDLED;
            }
            DiffCursor on = to.get();
            notice = wrapped;
            currentMatch = Option.of(step.index());
            column = shownColumn(hits, hit);
            cursor = Option.of(on);
            state.scrollToTop();
            state.scrollDown(on.row());
            return scrolled(content, Option.of(new Found.InStop(on.anchor(), hits.nth(step.index(), on.stopStart()))));
        }
        int top = state.scrollRow();
        int height = Math.max(1, state.viewportHeight());
        int row = hit.row() >= top && hit.row() < top + height ? top : Math.max(0, hit.row() - height / 2);
        notice = wrapped;
        currentMatch = Option.of(step.index());
        column = shownColumn(hits, hit);
        state.scrollToTop();
        state.scrollDown(row);
        return scrolled(content, Option.of(new Found.OnLine(hit.row(), hits.nth(step.index(), hit.row()))));
    }

    /**
     * How far the area is scrolled sideways to show {@code hit} of {@code hits}: as far as it is, when it shows the
     * match already; else so the match starts at its left edge, or ends at its right one.
     */
    private int shownColumn(SearchHits hits, SearchHits.Hit hit) {
        return styled.map(shown -> {
                    String text = shown.lines().get(hit.row()).rawContent();
                    int from = Math.min(
                            text.length(), shown.lines().source(hit.row()).from() + hit.start());
                    int until = Math.min(text.length(), from + hits.query().length());
                    int start = Span.raw(text.substring(0, from)).width();
                    int end = Span.raw(text.substring(0, until)).width();
                    int width = Math.max(1, element.state().viewportWidth());
                    int shifted = start < column ? start : end > column + width ? end - width : column;
                    return Math.clamp(shifted, 0, Math.max(0, columns));
                })
                .getOrElse(column);
    }

    /**
     * What the title of the area says of the search after what it says already (ADR 126): the query and which of the
     * matches is current, else how many there are, or that there are none; and what {@link #notice} says.
     */
    private String searchTitle(Option<SearchHits> hits) {
        return hits.map(found -> found.isEmpty()
                                ? Messages.get("content.search.none", found.query())
                                : currentMatch
                                        .map(at -> Messages.get(
                                                "content.search.current", found.query(), at + 1, found.size()))
                                        .getOrElse(() ->
                                                Messages.get("content.search.count", found.query(), found.size())))
                        .getOrElse("")
                + notice.map(says -> Messages.get("content.search.notice", says))
                        .getOrElse("");
    }

    /**
     * Sends {@code msg}, which a key of the diff of a pull request made, telling the area first where it scrolls
     * to if it does.
     */
    private EventResult reviewed(Msg msg) {
        if (msg instanceof ViewMsg.ContentScrolled(var ignored, Scrolled to)) {
            told(to);
        }
        dispatch.accept(msg);
        return EventResult.HANDLED;
    }

    /**
     * Where {@code cursor} is, as the keys of the diff of a pull request act on it, with where the keys that move
     * it take it.
     */
    private DiffCursorAt at(DiffCursor cursor, ScrolledContent content) {
        Anchor acting = acting(cursor);
        return new DiffCursorAt(
                area.id(),
                new Scrolled(content, cursor.row(), column, Option.of(acting), cursor.markAnchor()),
                acting,
                cursor.nextThread().map(next -> movedTo(next, content)),
                cursor.previousThread().map(previous -> movedTo(previous, content)),
                cursor.marked().map(marked -> movedTo(marked, content)));
    }

    /**
     * What a key acts on with the cursor on {@code cursor}: the stop it is on, but on the bar of a version
     * rendered, which is its only stop, the first line of the block at the top of the area, so a version shown
     * as source again has the cursor there (ADR 103).
     */
    private Anchor acting(DiffCursor cursor) {
        return cursor.anchor() instanceof Anchor.File
                ? styled.flatMap(shown -> shown.top(cursor.row())).getOrElse(cursor::anchor)
                : cursor.anchor();
    }

    private Msg movedTo(DiffCursor cursor, ScrolledContent content) {
        return new ViewMsg.ContentScrolled(area.id(), scrolledTo(cursor, content));
    }

    private Scrolled scrolledTo(DiffCursor cursor, ScrolledContent content) {
        return new Scrolled(content, cursor.row(), column, Option.of(cursor.anchor()), cursor.markAnchor());
    }

    /**
     * Where the keys that move the cursor of a rich diff take it, if {@code event} is one of them.
     */
    private static Option<DiffCursor> move(DiffCursor cursor, KeyEvent event) {
        return Option.<DiffCursor>none()
                .orElse(Option.when(event.matches(Actions.MOVE_UP) || event.isChar(UP), cursor::up))
                .orElse(Option.when(event.matches(Actions.MOVE_DOWN) || event.isChar(DOWN), cursor::down))
                .orElse(Option.when(event.matches(Actions.PAGE_UP), cursor::pageUp))
                .orElse(Option.when(event.matches(Actions.PAGE_DOWN), cursor::pageDown))
                .orElse(Option.when(event.matches(Actions.HOME), cursor::home))
                .orElse(Option.when(event.matches(Actions.END), cursor::end));
    }

    /**
     * Shows the cursor {@code moved}, and tells the model where it is and where the area is scrolled to, in one
     * message, so the model never has the cursor off the screen.
     */
    private EventResult moved(DiffCursor moved, ScrolledContent content) {
        cursor = Option.of(moved);
        RichTextState state = element.state();
        state.scrollToTop();
        state.scrollDown(moved.row());
        return scrolled(content);
    }

    /**
     * A click gives the focus to an area, even from the panes, and puts the cursor of a rich diff
     * on the line clicked; the wheel scrolls.
     */
    private EventResult mouse(MouseEvent event, ScrolledContent content) {
        RichTextState state = element.state();
        if (event.kind() == MouseEventKind.PRESS && event.isLeftButton()) {
            focus.accept(id);
            // The text starts a line below the top of the area, under its border.
            return cursor.flatMap(on ->
                            Option.of(element.renderedArea()).flatMap(drawn -> on.clicked(event.y() - drawn.top() - 1)))
                    .map(clicked -> {
                        EventResult result = moved(clicked, content);
                        // A click on a marker shows all the lines it leaves out, as Enter on it does (ADR 77).
                        if (clicked.anchor() instanceof Anchor.Gap) {
                            KEYMAP.handle(Scopes.DIFF, KeyEvent.ofKey(KeyCode.ENTER), Option.of(at(clicked, content)))
                                    .forEach(dispatch);
                        }
                        return result;
                    })
                    .getOrElse(EventResult.HANDLED);
        }
        if (event.kind() == MouseEventKind.SCROLL_UP) {
            return cursor.map(on -> moved(on.scrolled(-WHEEL_LINES), content)).getOrElse(() -> {
                state.scrollUp(WHEEL_LINES);
                return scrolled(content);
            });
        }
        if (event.kind() == MouseEventKind.SCROLL_DOWN) {
            return cursor.map(on -> moved(on.scrolled(WHEEL_LINES), content)).getOrElse(() -> {
                state.scrollDown(WHEEL_LINES);
                return scrolled(content);
            });
        }
        return EventResult.UNHANDLED;
    }

    /**
     * Scrolls the area as it does on {@code event}, and says whether it was a key for that: up and down its
     * element, sideways itself.
     */
    private boolean scroll(KeyEvent event) {
        RichTextState state = element.state();
        return Option.<Runnable>none()
                .orElse(Option.when(event.matches(Actions.MOVE_UP), () -> state::scrollUp))
                .orElse(Option.when(event.matches(Actions.MOVE_DOWN), () -> state::scrollDown))
                .orElse(Option.when(event.matches(Actions.PAGE_UP), () -> state::pageUp))
                .orElse(Option.when(event.matches(Actions.PAGE_DOWN), () -> state::pageDown))
                .orElse(Option.when(event.matches(Actions.HOME), () -> state::scrollToTop))
                .orElse(Option.when(event.matches(Actions.END), () -> state::scrollToBottom))
                .orElse(Option.when(allLeft(event), () -> () -> column = 0))
                .orElse(Option.when(allRight(event), () -> () -> column = columns))
                .orElse(Option.when(left(event), () -> () -> column = Math.max(0, column - 1)))
                .orElse(Option.when(right(event), () -> () -> column = Math.min(columns, column + 1)))
                .peek(Runnable::run)
                .isDefined();
    }

    private static boolean left(KeyEvent event) {
        return event.matches(Actions.MOVE_LEFT) || event.isChar(LEFT);
    }

    private static boolean right(KeyEvent event) {
        return event.matches(Actions.MOVE_RIGHT) || event.isChar(RIGHT);
    }

    // TamboUI's arrow actions also match the arrows with Shift: these are asked first.
    private static boolean allLeft(KeyEvent event) {
        return (event.isKey(KeyCode.LEFT) && event.hasShift()) || event.isChar(ALL_LEFT);
    }

    private static boolean allRight(KeyEvent event) {
        return (event.isKey(KeyCode.RIGHT) && event.hasShift()) || event.isChar(ALL_RIGHT);
    }

    /**
     * Tells the model where the area showing {@code content} is scrolled to now.
     */
    private EventResult scrolled(ScrolledContent content) {
        return scrolled(content, Option.none());
    }

    /**
     * Tells the model where the area showing {@code content} is scrolled to now, and the match of the search it went
     * to, if it did (ADR 126): any other move lets go of it.
     */
    private EventResult scrolled(ScrolledContent content, Option<Found> found) {
        RichTextState state = element.state();
        Scrolled to = new Scrolled(
                content,
                state.scrollRow(),
                column,
                cursor.map(DiffCursor::anchor),
                cursor.flatMap(DiffCursor::markAnchor),
                found);
        told(to);
        dispatch.accept(new ViewMsg.ContentScrolled(area.id(), to));
        return EventResult.HANDLED;
    }

    /**
     * Remembers that the model was told the area is now at {@code to}.
     */
    private void told(Scrolled to) {
        sent = Option.of(Tuple.of(sent.map(Tuple2::_1).getOrElse(shownAt), to));
    }

    /**
     * The text area {@code fitted}, its lines fitted to the size it was last drawn in, drawn so they fit the size
     * it is drawn in (ADR 80). The text area only learns its size while it draws, so a frame that draws it in
     * another size, like one after the panes are resized, draws it once to let it learn it, puts back the cells
     * that drew, and draws it {@code again}, fitted anew. Drawn twice, it is still given the events and the focus
     * once, as TamboUI registers an element and its id once per frame.
     */
    private final class Fitted implements Element {

        private final RichTextAreaElement fitted;
        private final Supplier<RichTextAreaElement> again;

        Fitted(RichTextAreaElement fitted, Supplier<RichTextAreaElement> again) {
            this.fitted = fitted;
            this.again = again;
        }

        @Override
        public void render(Frame frame, Rect at, RenderContext context) {
            if (at.width() == drawnWidth && at.height() == drawnHeight) {
                fitted.render(frame, at, context);
            } else {
                drawnWidth = at.width();
                drawnHeight = at.height();
                Buffer buffer = frame.buffer();
                Rect cells = buffer.area().intersection(at);
                Vector<Cell> before = Vector.range(cells.top(), cells.bottom())
                        .flatMap(y -> Vector.range(cells.left(), cells.right()).map(x -> buffer.get(x, y)));
                fitted.render(frame, at, context);
                before.forEachWithIndex((cell, index) ->
                        buffer.set(cells.left() + index % cells.width(), cells.top() + index / cells.width(), cell));
                again.get().render(frame, at, context);
            }
            bars(frame, at);
        }

        /**
         * Draws on the borders of the area, drawn in {@code at}, how far it is scrolled: down on its right border
         * while it has more lines than show, sideways on its bottom border while its lines are wider than it. On
         * the border, a bar takes no column or line from the text.
         */
        private void bars(Frame frame, Rect at) {
            if (at.width() < 3 || at.height() < 3) {
                return;
            }
            RichTextState state = element.state();
            int height = state.viewportHeight();
            int width = state.viewportWidth();
            if (state.contentHeight() > height) {
                onBorder(
                        frame,
                        new Rect(at.right() - 1, at.y() + 1, 1, at.height() - 2),
                        ScrollbarOrientation.VERTICAL_RIGHT,
                        BorderBar.DOWNWARDS_THUMB,
                        new ScrollbarState()
                                .contentLength(state.contentHeight())
                                .viewportContentLength(height)
                                .position(state.scrollRow()));
            }
            if (columns > 0) {
                onBorder(
                        frame,
                        new Rect(at.x() + 1, at.bottom() - 1, at.width() - 2, 1),
                        ScrollbarOrientation.HORIZONTAL_BOTTOM,
                        BorderBar.SIDEWAYS_THUMB,
                        new ScrollbarState()
                                .contentLength(columns + width)
                                .viewportContentLength(width)
                                .position(column));
            }
        }

        /**
         * Draws a bar {@code scrolled} over the side of the border at {@code border}. Its track is the line the
         * border is drawn with, single or double as the theme has it, in the colour of the border; its thumb, a
         * heavy line, is in the terminal's own colour.
         */
        private void onBorder(
                Frame frame, Rect border, ScrollbarOrientation orientation, String thumb, ScrollbarState scrolled) {
            frame.renderStatefulWidget(
                    Scrollbar.builder()
                            .orientation(orientation)
                            .trackSymbol(
                                    frame.buffer().get(border.x(), border.y()).symbol())
                            .thumbSymbol(thumb)
                            .thumbStyle(BorderBar.THUMB)
                            .build(),
                    border,
                    scrolled);
        }

        @Override
        public Constraint constraint() {
            return fitted.constraint();
        }

        @Override
        public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
            return fitted.preferredSize(availableWidth, availableHeight, context);
        }
    }

    /**
     * {@code body}, styled in {@code theme} as {@code lines}, each drawn when it shows ({@link Rows}), with where
     * the stops of its cursor start, if it has one.
     *
     * @param asGiven the hunk shown as git gives it, as a range is open in it (ADR 83)
     * @param widest  how wide the widest of {@code lines} is, which the tint of the cursor never makes wider
     * @param places  where each line of a version rendered is drawn, in order (ADR 103)
     * @param width   how wide a version rendered was drawn
     * @param hits    the matches of the search in {@code lines}, while one is kept (ADR 126)
     */
    private record Styled(
            Body body,
            ThemeName theme,
            Option<DiffStops.HunkAt> asGiven,
            Rows lines,
            Vector<RichDiff.Stop> stops,
            int widest,
            Vector<RichDiff.Place> places,
            int width,
            Option<SearchHits> hits) {

        static Styled of(
                Body body, ThemeName theme, Option<DiffStops.HunkAt> asGiven, RichDiff.Drawn drawn, int width) {
            Rows lines = drawn.rows();
            Vector<RichDiff.Stop> stops = drawn.stops().toVector();
            return new Styled(
                    body,
                    theme,
                    asGiven,
                    lines,
                    stops,
                    // The render thread waits for it, as it does for the lines to be drawn; the lines of a diff
                    // are not drawn for it.
                    drawn.widest().getAsInt(),
                    drawn.places().toVector(),
                    width,
                    Option.none());
        }

        /**
         * This body with {@code kept}, its lines with others kept drawn.
         */
        Styled withLines(Rows kept) {
            return new Styled(body, theme, asGiven, kept, stops, widest, places, width, hits);
        }

        /**
         * This body with the matches of {@code query} in it, found again only for another query.
         */
        Styled searched(Option<String> query) {
            Option<SearchHits> found = query.map(text ->
                    hits.filter(those -> those.query().equals(text)).getOrElse(() -> SearchHits.of(lines, text)));
            return new Styled(body, theme, asGiven, lines, stops, widest, places, width, found);
        }

        /**
         * The index among {@code hits} of the match {@code found}, if it is still one of this body: the {@code nth}
         * of the rows of its stop, whose stop is looked for from the stop at {@code near}, or of its line.
         */
        Option<Integer> match(SearchHits hits, Found found, int near) {
            return switch (found) {
                case Found.InStop(Anchor stop, int nth) ->
                    DiffCursor.index(stops, stop, near)
                            .flatMap(index -> hits.at(
                                    stops.get(index).row(),
                                    index + 1 < stops.size()
                                            ? stops.get(index + 1).row()
                                            : lines.size(),
                                    nth));
                case Found.OnLine(int line, int nth) -> hits.at(line, line + 1, nth);
            };
        }

        /**
         * Whether line {@code row} is drawn to the edge of the area (ADR 80): the bar of each file, the lines of
         * the diff and the markers of the lines it leaves out, not the threads on them. The stops are in the order
         * of their lines, so those that start on it are looked up, not kept apart for each line.
         */
        boolean filling(int row) {
            return stops.drop(firstFrom(row, 0, stops.size()))
                    .takeWhile(stop -> stop.row() == row)
                    .exists(stop -> switch (stop.mark().anchor()) {
                        case Anchor.File _, Anchor.OnLine _, Anchor.Gap _, Anchor.Node _ -> true;
                        case Anchor.Lead _, Anchor.Thread _, Anchor.Comment _ -> false;
                    });
        }

        /**
         * The index of the first of the stops from {@code low} up to {@code high} that starts on or after
         * {@code row}, found by halving.
         */
        private int firstFrom(int row, int low, int high) {
            if (low >= high) {
                return low;
            }
            int middle = (low + high) >>> 1;
            return stops.get(middle).row() < row ? firstFrom(row, middle + 1, high) : firstFrom(row, low, middle);
        }

        /**
         * The hunk of the line {@code anchor}, if it is a line of this body, looked for from the stop at
         * {@code near} ({@link DiffCursor#index}).
         */
        Option<DiffStops.HunkAt> hunk(Anchor anchor, int near) {
            return DiffCursor.index(stops, anchor, near)
                    .flatMap(index -> stops.get(index).mark().hunk());
        }

        /**
         * The line the stop {@code anchor} starts on, if it is a stop of this body, looked for from the stop at
         * {@code near} ({@link DiffCursor#index}).
         */
        Option<Integer> row(Anchor anchor, int near) {
            return DiffCursor.index(stops, anchor, near)
                    .map(index -> stops.get(index).row())
                    .orElse(() ->
                            places.find(place -> place.anchor().equals(anchor)).map(RichDiff.Place::row));
        }

        /**
         * The first line of the block of a version rendered at the top of the area scrolled to {@code row}, or of
         * its first block when none is at or above it; none for a body that is no version rendered (ADR 103).
         */
        Option<Anchor> top(int row) {
            return places.filter(place -> place.row() <= row)
                    .lastOption()
                    // Above the first block, as with the bar of the file at the top, the first block.
                    .orElse(places.headOption())
                    .flatMap(last -> places.find(place -> place.row() == last.row()))
                    .map(RichDiff.Place::anchor);
        }
    }

    /**
     * How a body is drawn: with the stops of its cursor, the hunk a range is open in as git gives it (ADR 83), and a
     * version of a file rendered as wide as its area (ADR 103).
     */
    @FunctionalInterface
    public interface Styler {

        RichDiff.Drawn drawn(Body body, Option<DiffStops.HunkAt> asGiven, int width);
    }
}
