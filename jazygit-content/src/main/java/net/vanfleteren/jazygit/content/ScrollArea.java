package net.vanfleteren.jazygit.content;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.RichTextState;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.RichTextAreaElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.tui.event.PasteEvent;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarOrientation;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.content.AreaState.DrawnIn;
import net.vanfleteren.jazygit.content.ContentView.Body;
import net.vanfleteren.jazygit.content.ContentView.Shown;
import net.vanfleteren.jazygit.content.diff.BlameFooter;
import net.vanfleteren.jazygit.content.diff.DiffCursor;
import net.vanfleteren.jazygit.content.diff.RichDiff;
import net.vanfleteren.jazygit.state.Focus;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.diff.DiffStops;
import net.vanfleteren.jazygit.state.view.ContentSearchMsg;
import net.vanfleteren.jazygit.ui.theme.Theme;
import net.vanfleteren.jazygit.ui.widgets.BorderBar;
import net.vanfleteren.jazygit.ui.widgets.FilterLine;
import net.vanfleteren.jazygit.ui.widgets.PasteTarget;

/**
 * A scrollable area of the content panel. It shows what it is given at the position the model has for that, and tells
 * where the user scrolled it to. What it knows of itself between frames is one {@link AreaState}, which
 * {@link AreaFit} works out anew each frame and the keys and the mouse turn into another; the element only keeps the
 * layout of the text, and how far down it is scrolled.
 */
public final class ScrollArea {

    private final ContentArea area;
    private final String id;
    private final RichTextAreaElement element;
    private final BooleanSupplier leaveScrollArea;
    private final Consumer<Focus> focus;
    private final Consumer<Msg> dispatch;
    // The lines to type the query of a search in (ADR 126), and a line number (ADR 140), which keep where their cursor
    // is; their text is the area's.
    private final FilterLine searchLine = new FilterLine();
    private final FilterLine numberLine = new FilterLine(AreaLines.GO_TO_LINE);
    private final AreaKeys.Edits edits = new AreaKeys.Edits(searchLine::edit, numberLine::edit);
    private AreaState state;

    public ScrollArea(
            ContentArea area, BooleanSupplier leaveScrollArea, Consumer<Focus> focus, Consumer<Msg> dispatch) {
        this.area = area;
        this.id = area.id().value();
        this.leaveScrollArea = leaveScrollArea;
        this.focus = focus;
        this.dispatch = dispatch;
        this.state = AreaState.initial(area.id());
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
    public Element show(Model model, Option<Focus> focus, Shown.Area shown, Styler style, Theme.SpanStyles styles) {
        boolean focused = focus.contains(area.id());
        AreaFit.Showing showing = new AreaFit.Showing(model, focused, shown, style, styles);
        Element fitted = new Fitted(fitted(showing), () -> fitted(showing));
        // The line to type the query of a search in, or a line number, above the area that has the focus, which keeps
        // it (ADR 126, 140).
        Option<StyledElement<?>> line = state.search()
                .typing()
                .filter(ignored -> focused)
                .<StyledElement<?>>map(searchLine::field)
                .orElse(() -> state.line().number().filter(ignored -> focused).map(numberLine::field));
        Element shownWithLine = line.<Element>map(
                        field -> new PasteTarget(column(field, fitted).fill(), id, this::pasted))
                .getOrElse(fitted);
        // Under a version shown blamed, the commit of the line of the cursor (ADR 147).
        return model.repoView()
                .version(area.id(), shown.content())
                .flatMap(version -> BlameFooter.text(version, state.cursor().map(DiffCursor::anchor), model.now()))
                .<Element>map(said -> column(
                                shownWithLine,
                                text(" " + said).addClass(Theme.Classes.DIM).length(1))
                        .fill())
                .getOrElse(shownWithLine);
    }

    /**
     * The text area as {@code showing} shows it, its lines fitted to the size the area was last drawn in.
     */
    private RichTextAreaElement fitted(AreaFit.Showing showing) {
        RichTextState scroll = element.state();
        AreaFit.Fitted fitted = AreaFit.fit(state, showing, Viewport.of(scroll));
        state = fitted.after();
        AreaFit.Drawing drawing = fitted.drawing();
        element.text(drawing.text()).title(drawing.title());
        // The state clamps a scroll to the lines it was last drawn with: a longer body, as a version of the file in
        // place of its diff, would show only the blank lines around where it was cut off (ADR 78, 102).
        scroll.setContentHeight(drawing.lines());
        scroll.scrollToTop();
        scroll.scrollDown(drawing.row());
        return element.onKeyEvent(this::keys).onMouseEvent(this::mouse);
    }

    private void pasted(PasteEvent event) {
        state.search()
                .typing()
                .forEach(text -> dispatch.accept(new ContentSearchMsg.Edited(searchLine.paste(text, event))));
        state.line()
                .number()
                .filter(ignored -> state.search().typing().isEmpty())
                .map(text -> numberLine.paste(text, event))
                .filter(AreaLines::isNumber)
                .map(text -> AreaLines.edited(state, Viewport.of(element.state()), text))
                .forEach(this::apply);
    }

    /**
     * What {@code event} does ({@link AreaKeys}): the area leaves on it, if nothing before that acts on it.
     */
    private EventResult keys(KeyEvent event) {
        Reaction before = AreaKeys.beforeLeaving(state, Viewport.of(element.state()), event, edits);
        if (before.handled()) {
            return applied(before);
        }
        // Key handlers also see keys typed elsewhere; the callback checks that an area has focus.
        if (AreaKeys.leaving(state, event) && leaveScrollArea.getAsBoolean()) {
            return applied(Reaction.handled(before.state(), before.view()));
        }
        return applied(AreaKeys.afterLeaving(before.state(), before.view(), event));
    }

    /**
     * What {@code event} does ({@link AreaMouse}): a click gives the focus to the area first, even from the panes.
     */
    private EventResult mouse(MouseEvent event) {
        if (event.kind() == MouseEventKind.PRESS && event.isLeftButton()) {
            focus.accept(area.id());
        }
        return applied(AreaMouse.mouse(state, Viewport.of(element.state()), event, Option.of(element.renderedArea())));
    }

    /**
     * Takes on the area {@code reaction} leaves, scrolled where it says, then sends what it sends.
     */
    private EventResult applied(Reaction reaction) {
        apply(reaction);
        return reaction.handled() ? EventResult.HANDLED : EventResult.UNHANDLED;
    }

    private void apply(Reaction reaction) {
        state = reaction.state();
        RichTextState scroll = element.state();
        scroll.scrollToTop();
        scroll.scrollDown(reaction.view().top());
        reaction.sent().forEach(dispatch);
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
            DrawnIn size = new DrawnIn(at.width(), at.height());
            if (state.drawnIn().contains(size)) {
                fitted.render(frame, at, context);
            } else {
                state = state.withDrawnIn(Option.of(size));
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
            RichTextState scroll = element.state();
            int height = scroll.viewportHeight();
            int width = scroll.viewportWidth();
            if (scroll.contentHeight() > height) {
                onBorder(
                        frame,
                        new Rect(at.right() - 1, at.y() + 1, 1, at.height() - 2),
                        ScrollbarOrientation.VERTICAL_RIGHT,
                        BorderBar.DOWNWARDS_THUMB,
                        new ScrollbarState()
                                .contentLength(scroll.contentHeight())
                                .viewportContentLength(height)
                                .position(scroll.scrollRow()));
            }
            AreaState.Columns columns = state.columns();
            if (columns.widest() > 0) {
                onBorder(
                        frame,
                        new Rect(at.x() + 1, at.bottom() - 1, at.width() - 2, 1),
                        ScrollbarOrientation.HORIZONTAL_BOTTOM,
                        BorderBar.SIDEWAYS_THUMB,
                        new ScrollbarState()
                                .contentLength(columns.widest() + width)
                                .viewportContentLength(width)
                                .position(columns.column()));
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
     * How a body is drawn: with the stops of its cursor, the hunk a range is open in as git gives it (ADR 83), and a
     * version of a file rendered as wide as its area (ADR 103).
     */
    @FunctionalInterface
    public interface Styler {

        RichDiff.Drawn drawn(Body body, Option<DiffStops.HunkAt> asGiven, int width);
    }
}
