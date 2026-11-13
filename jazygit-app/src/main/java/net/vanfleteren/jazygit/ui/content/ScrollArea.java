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
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.state.diff.DiffStops;
import net.vanfleteren.jazygit.state.diff.Scrolled;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.state.view.ViewMsg;
import net.vanfleteren.jazygit.ui.ContentArea;
import net.vanfleteren.jazygit.ui.ContentView.Body;
import net.vanfleteren.jazygit.ui.ContentView.Shown;
import net.vanfleteren.jazygit.ui.DiffCursor;
import net.vanfleteren.jazygit.ui.RichDiff;
import net.vanfleteren.jazygit.ui.Rows;
import net.vanfleteren.jazygit.ui.Theme;

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
    // column it never draws at, so the area draws its lines with as many columns cut off their start itself.
    private int column = 0;
    private int columns = 0;
    // The size the area was last drawn in, which its lines are fitted to; none before it is first drawn (ADR 80).
    private int drawnWidth = -1;
    private int drawnHeight = -1;

    public ScrollArea(
            ContentArea area, BooleanSupplier leaveScrollArea, Consumer<String> focus, Consumer<Msg> dispatch) {
        this.area = area;
        this.id = area.id().value();
        this.leaveScrollArea = leaveScrollArea;
        this.focus = focus;
        this.dispatch = dispatch;
        element = richTextArea().id(id).scrollbar().fill();
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
        return new Fitted(
                fit(model, focusedId, shown, style, styles), () -> fit(model, focusedId, shown, style, styles));
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
        Styled current = last.filter(before -> before.asGiven().equals(asGiven))
                .getOrElse(() -> Styled.of(body, theme, asGiven, style.drawn(body, asGiven, width), width));
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
                            told.content(), Math.max(0, told.row() + shift), told.column(), told.cursor(), told.mark());
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
        // The size the element was last drawn in, which a frame in another size draws it in first (ADR 80).
        columns = Math.max(0, current.widest() - state.viewportWidth());
        column = Math.clamp(at.column(), 0, columns);
        int row = cursor.map(DiffCursor::row).getOrElse(at.row());
        // The lines that show are kept drawn for the frames after, and only they ({@link Rows}).
        Tuple2<Integer, Integer> window = window(current.lines().size(), row, state.viewportHeight());
        Rows kept = current.lines().keeping(window._1(), window._2());
        styled = Option.of(current.withLines(kept));
        Rows lines = cursor.filter(ignored -> focused)
                .map(on -> on.tinted(kept, styles.of(Theme.Classes.DIFF_CURSOR)))
                .getOrElse(kept);
        element.text(windowed(lines, column, row, state.viewportHeight(), state.viewportWidth(), current::filling))
                .title(shown.title());
        // The state clamps a scroll to the lines it was last drawn with: a longer body, as a version of the file
        // in place of its diff, would show only the blank lines around where it was cut off (ADR 78, 102).
        state.setContentHeight(current.lines().size());
        state.scrollToTop();
        state.scrollDown(row);
        return element.onKeyEvent(event -> keys(event, focused, content)).onMouseEvent(event -> mouse(event, content));
    }

    /**
     * What {@code event} does: Left leaves the area as Escape does once it is scrolled all the way left, as Right in
     * its pane moves into it.
     */
    private EventResult keys(KeyEvent event, boolean focused, ScrolledContent content) {
        // Only the arrow leaves: h scrolls, as j and k move, without going to another panel.
        boolean leaving = event.isCancel() || (focused && event.matches(Actions.MOVE_LEFT) && column == 0);
        // Escape, and Left where it leaves as Escape does, drop an open range before they leave the area (ADR 60).
        return cursor.filter(on -> leaving && focused && on.mark().isDefined())
                .map(ranged -> moved(ranged.marked().get(), content))
                // Key handlers also see keys typed elsewhere; the callback checks that an area has focus.
                .orElse(() -> Option.when(leaving && leaveScrollArea.getAsBoolean(), EventResult.HANDLED))
                .orElse(() -> Option.when(!focused, EventResult.UNHANDLED))
                .orElse(() -> cursor.flatMap(on -> move(on, event)).map(to -> moved(to, content)))
                .orElse(() -> cursor.flatMap(on -> KEYMAP.handle(
                                review ? Scopes.PULL_REQUEST_DIFF : Scopes.DIFF, event, Option.of(at(on, content))))
                        .map(this::reviewed))
                .getOrElse(() -> scroll(event) ? scrolled(content) : EventResult.UNHANDLED);
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

    /**
     * Tells the model where the area showing {@code content} is scrolled to now.
     */
    private EventResult scrolled(ScrolledContent content) {
        RichTextState state = element.state();
        Scrolled to = new Scrolled(
                content,
                state.scrollRow(),
                column,
                cursor.map(DiffCursor::anchor),
                cursor.flatMap(DiffCursor::markAnchor));
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
                return;
            }
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
     */
    private record Styled(
            Body body,
            ThemeName theme,
            Option<DiffStops.HunkAt> asGiven,
            Rows lines,
            Vector<RichDiff.Stop> stops,
            int widest,
            Vector<RichDiff.Place> places,
            int width) {

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
                    width);
        }

        /**
         * This body with {@code kept}, its lines with others kept drawn.
         */
        Styled withLines(Rows kept) {
            return new Styled(body, theme, asGiven, kept, stops, widest, places, width);
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
                        case Anchor.File _, Anchor.OnLine _, Anchor.Gap _ -> true;
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
