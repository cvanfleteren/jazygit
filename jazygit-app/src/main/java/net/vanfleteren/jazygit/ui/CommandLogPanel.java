package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.richText;
import static dev.tamboui.toolkit.Toolkit.richTextArea;
import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.RichTextState;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.elements.RichTextAreaElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.bindings.Actions;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.operation.Outcome;
import net.vanfleteren.jazygit.ui.text.ErrorText;
import net.vanfleteren.jazygit.ui.text.NoticeText;
import net.vanfleteren.jazygit.ui.text.TextWidth;
import net.vanfleteren.jazygit.ui.theme.Theme;
import net.vanfleteren.jazygit.ui.widgets.BorderBar;

/**
 * Right-hand panel under the content panel, logging the mutating git commands that were executed, each
 * under a title, followed by why the last operation failed, when it did, or by a notice about it (ADR 129).
 *
 * <p>It is as tall as its lines, up to {@link #VISIBLE_LINES}, and the error or notice as it wraps: empty, it is its
 * borders and title alone. The content panel above it comes first: the log takes only the rows the content panel can
 * spare above {@link #CONTENT_MIN_ROWS}, and no fewer than its borders and a row of the error or notice, so a failure
 * always shows. While the content panel is maximized, by collapsing the left column (ADR 104), the log shows the
 * error or notice alone.
 *
 * <p>{@code @} or a click gives it the focus, which no Tab or arrow does: it then grows to half the column, or as tall
 * as all it logged when that is less, and shows all of it, wrapped, from the newest, to scroll through. Escape gives
 * the focus back to where it was.
 */
public final class CommandLogPanel {

    /**
     * The id the panel has the focus by.
     */
    public static final String ID = "command-log";

    /**
     * How many lines of commands fit in the panel at most; the oldest ones scroll off.
     */
    public static final int VISIBLE_LINES = 8;

    /**
     * How many rows the content panel keeps before the log takes any for its lines.
     */
    public static final int CONTENT_MIN_ROWS = 12;

    /**
     * The rows the borders of the panel take, its title on the top one, and the columns they take.
     */
    public static final int BORDERS = 2;

    private static final String INDENT = "  ";

    /**
     * How many rows a turn of the wheel scrolls, as in the areas of the content panel.
     */
    private static final int WHEEL_ROWS = 3;

    /**
     * How the time an operation was done is shown, before its title.
     */
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern(Messages.get("panel.commandLog.timePattern"));

    private final Theme.SpanStyles styles;
    private final ZoneId zone;
    private final Consumer<String> focus;
    private final Runnable leave;
    // The text area the log is drawn in while it has the focus, which keeps where it is scrolled to; whether it had
    // the focus in the frame before, and the rows it was wrapped into last, for the outcome and the width they were
    // wrapped for. Only used from the render thread.
    private final RichTextAreaElement area = richTextArea().id(ID).fill();
    private boolean focusedBefore = false;
    private Option<Wrapped> wrapped = Option.none();
    // Whether it shows the newest lines, and keeps showing them as it slides and as lines are added: from when it is
    // given the focus until it is scrolled up, and again once it is scrolled to the bottom.
    private boolean following = false;
    // Whether it has the focus, as last drawn: a key or a turn of the wheel meant for a popup over it does nothing.
    private boolean hasFocus = false;

    /**
     * @param styles how the lines are coloured
     * @param zone   the time zone the time an operation was done is shown in
     * @param focus  gives the focus to the element with the id given, for a click on the log
     * @param leave  gives the focus back to where it was before the log had it, for Escape
     */
    public CommandLogPanel(Theme.SpanStyles styles, ZoneId zone, Consumer<String> focus, Runnable leave) {
        this.styles = styles;
        this.zone = zone;
        this.focus = focus;
        this.leave = leave;
    }

    /**
     * How many rows the panel takes in {@code room}, when that is known, without the focus: its lines and borders, as
     * many lines as the content panel can spare above {@link #CONTENT_MIN_ROWS}, but at least its borders, and a row
     * of the error or notice when there is one. The error or notice takes the rows it wraps onto, even past
     * {@link #VISIBLE_LINES}.
     */
    public static int height(Model model, Option<Room> room) {
        int outcome = outcomeRows(model, room);
        int wanted = BORDERS + Math.max(outcome, Math.min(VISIBLE_LINES, logLineCount(model) + outcome));
        int spare = room.map(column -> column.rows() - CONTENT_MIN_ROWS).getOrElse(wanted);
        return Math.max(BORDERS + Math.min(1, outcome), Math.min(wanted, spare));
    }

    /**
     * How many rows the panel takes in {@code room} with the focus: half the column, or as many as all it logged
     * takes, wrapped, and its borders, when that is less; but no fewer than without the focus.
     */
    public int focusedHeight(Model model, Option<Room> room) {
        return room.map(column -> Math.max(
                        height(model, room),
                        Math.min(
                                column.rows() / 2, BORDERS + rows(model, column).size())))
                .getOrElse(() -> height(model, room));
    }

    /**
     * The panel {@code rows} tall in {@code room}, drawn {@code shown} rows tall on its way there: {@code open}, all it
     * logged, to scroll through while it has the {@code focused} focus; else what {@link #fitted} shows, which a click
     * gives the focus. It is open with the focus, and while a popup opened from it has the focus, which gives it back.
     */
    public Element render(Model model, int rows, int shown, Option<Room> room, boolean open, boolean focused) {
        hasFocus = focused;
        if (!open) {
            focusedBefore = false;
            return fitted(model, rows, room, styles, zone).id(ID).length(shown).onMouseEvent(this::clicked);
        }
        List<Row> all = room.map(column -> rows(model, column)).getOrElse(List.empty());
        RichTextState state = area.state();
        // The state clamps a scroll to what it was last drawn with: what it shows now is set first, in the rows it
        // is drawn in on its way to its height.
        state.setContentHeight(all.size());
        state.setViewportHeight(Math.max(0, shown - BORDERS));
        // Given the focus, it shows the newest lines, as it did without it.
        following = following || !focusedBefore;
        if (following) {
            state.scrollToBottom();
        }
        focusedBefore = true;
        area.text(Text.from(all.map(row -> line(row, styles)).toJavaList()))
                .title(Messages.get("panel.commandLog.title"))
                .focusable(focused)
                .onKeyEvent(this::keys)
                .onMouseEvent(this::wheeled);
        return new Barred(shown);
    }

    /**
     * The panel {@code rows} tall in {@code room} without the focus: the newest lines that fit in it above the error
     * or notice, which is kept out of the cut, and shows while a line fits.
     */
    static Panel fitted(Model model, int rows, Option<Room> room, Theme.SpanStyles styles, ZoneId zone) {
        int lines = Math.max(0, rows - BORDERS);
        Option<Element> outcome = outcome(model).filter(line -> lines > 0);
        List<Element> shown = logLines(model, zone)
                .takeRight(Math.max(0, lines - outcomeRows(model, room)))
                .<Element>map(row -> richText(Text.from(line(row, styles))))
                .appendAll(outcome);
        return panel(Messages.get("panel.commandLog.title"), shown.toJavaArray(Element[]::new));
    }

    /**
     * A click on the log gives it the focus.
     */
    private EventResult clicked(MouseEvent event) {
        if (event.kind() == MouseEventKind.PRESS && event.isLeftButton()) {
            focus.accept(ID);
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    /**
     * What {@code event} does with the focus on the log: Escape gives the focus back; the arrows and {@code j} and
     * {@code k} scroll a row, the pages a page, and Home and End, or {@code g} and {@code G}, to the top and the
     * bottom. Left and Right do nothing: the lines are wrapped. The keys that work everywhere work here too.
     */
    private EventResult keys(KeyEvent event) {
        if (!hasFocus) {
            return EventResult.UNHANDLED;
        }
        if (event.isCancel()) {
            leave.run();
            return EventResult.HANDLED;
        }
        RichTextState state = area.state();
        Option<Runnable> scroll = Option.<Runnable>none()
                .orElse(Option.when(event.matches(Actions.MOVE_DOWN) || plain(event, 'j'), () -> state::scrollDown))
                .orElse(Option.when(event.matches(Actions.MOVE_UP) || plain(event, 'k'), () -> state::scrollUp))
                .orElse(Option.when(event.matches(Actions.PAGE_DOWN), () -> state::pageDown))
                .orElse(Option.when(event.matches(Actions.PAGE_UP), () -> state::pageUp))
                .orElse(Option.when(event.matches(Actions.HOME) || plain(event, 'g'), () -> state::scrollToTop))
                .orElse(Option.when(event.matches(Actions.END) || plain(event, 'G'), () -> state::scrollToBottom))
                .orElse(Option.when(
                        event.matches(Actions.MOVE_LEFT) || event.matches(Actions.MOVE_RIGHT), () -> () -> {}));
        scroll.forEach(this::scrolled);
        return scroll.isDefined() ? EventResult.HANDLED : EventResult.UNHANDLED;
    }

    /**
     * The wheel scrolls the log with the focus three rows at a time, as it does the areas of the content panel.
     */
    private EventResult wheeled(MouseEvent event) {
        RichTextState state = area.state();
        return Option.<Runnable>none()
                .orElse(Option.when(event.kind() == MouseEventKind.SCROLL_UP, () -> () -> state.scrollUp(WHEEL_ROWS)))
                .orElse(Option.when(
                        event.kind() == MouseEventKind.SCROLL_DOWN, () -> () -> state.scrollDown(WHEEL_ROWS)))
                .filter(ignored -> hasFocus)
                .peek(this::scrolled)
                .map(scroll -> EventResult.HANDLED)
                .getOrElse(EventResult.UNHANDLED);
    }

    /**
     * Scrolls the log by {@code scroll}: it follows the newest lines again once it is at the bottom.
     */
    private void scrolled(Runnable scroll) {
        scroll.run();
        RichTextState state = area.state();
        following = state.scrollRow() >= state.contentHeight() - state.viewportHeight();
    }

    private static boolean plain(KeyEvent event, char c) {
        return event.isChar(c) && !event.hasCtrl() && !event.hasAlt();
    }

    /**
     * The rows all the log takes in {@code room}, wrapped at words inside its borders as they are drawn: the commands
     * of every operation under its title, oldest first, and the error or notice. Wrapped once for an outcome and a
     * width.
     */
    private List<Row> rows(Model model, Room room) {
        int width = room.columns() - BORDERS;
        Outcome outcome = model.outcome();
        Wrapped current = wrapped.filter(last -> last.outcome() == outcome && last.width() == width)
                .getOrElse(() -> new Wrapped(
                        outcome,
                        width,
                        entryRows(outcome, zone)
                                .appendAll(outcomeText(model).map(text -> Row.of(text, outcomeClass(model))))
                                .flatMap(row -> wrapped(row, width))));
        wrapped = Option.of(current);
        return current.rows();
    }

    /**
     * The rows {@code row} takes wrapped at words in {@code width} columns, as they are drawn. A title is wrapped in
     * the columns its time leaves: the time is on its first row, and the rows after it start as far in, under the
     * title.
     */
    private static List<Row> wrapped(Row row, int width) {
        return row.time()
                .map(time -> TextWidth.wrapped(row.text(), width - TextWidth.of(time) - 1)
                        .zipWithIndex()
                        .map(text -> new Row(
                                Option.of(text._2 == 0 ? time : " ".repeat(TextWidth.of(time))),
                                text._1,
                                row.cssClass())))
                .getOrElse(() -> TextWidth.wrapped(row.text(), width).map(text -> Row.of(text, row.cssClass())));
    }

    /**
     * How many lines {@link #logLines} has.
     */
    private static int logLineCount(Model model) {
        return model.preferences().leftCollapsed(model.screen())
                ? 0
                : model.outcome()
                        .commandLog()
                        .map(entry -> 1 + entry.commands().size())
                        .sum()
                        .intValue();
    }

    /**
     * The lines of the commands logged, each under the title of its operation and when it was done; none while the
     * content panel is maximized, which collapses the left column of the screen shown (ADR 104).
     */
    private static List<Row> logLines(Model model, ZoneId zone) {
        return model.preferences().leftCollapsed(model.screen()) ? List.empty() : entryRows(model.outcome(), zone);
    }

    /**
     * The lines of every operation of {@code outcome}: its title after the time it was done, in {@code zone}, and its
     * commands under it.
     */
    private static List<Row> entryRows(Outcome outcome, ZoneId zone) {
        return outcome.commandLog()
                .flatMap(entry -> List.of(new Row(
                                Option.of(TIME.format(entry.at().atZone(zone))),
                                Messages.get(entry.operation().logTitleKey()),
                                Theme.Classes.BOLD))
                        .appendAll(
                                entry.commands().map(command -> Row.of(INDENT + command.line(), Theme.Classes.DIM))));
    }

    /**
     * {@code row} as it is drawn in {@code styles}: in the style of its class, after its time, dimmed, if it has one.
     */
    private static Line line(Row row, Theme.SpanStyles styles) {
        Span text = Span.styled(row.text(), styles.of(row.cssClass()));
        return row.time()
                .map(time -> Line.from(Span.styled(time + " ", styles.of(Theme.Classes.DIM)), text))
                .getOrElse(() -> Line.from(text));
    }

    /**
     * Why the last operation failed, when it did, or else a notice about it, wrapped at words.
     */
    private static Option<Element> outcome(Model model) {
        return outcomeText(model)
                .map(text -> text(text).addClass(outcomeClass(model)))
                .map(line -> line.overflow(Overflow.WRAP_WORD));
    }

    private static String outcomeClass(Model model) {
        return model.outcome().error().isDefined() ? Theme.Classes.ERROR : Theme.Classes.NOTICE;
    }

    private static Option<String> outcomeText(Model model) {
        return model.outcome()
                .error()
                .map(ErrorText::of)
                .orElse(() -> model.outcome().notice().map(NoticeText::of));
    }

    /**
     * How many rows the error or notice takes, wrapped at words in the panel in {@code room} as it is drawn, counted
     * no further than the rows of the column; one row when the room is not known, and none without one.
     */
    private static int outcomeRows(Model model, Option<Room> room) {
        return outcomeText(model)
                .map(text -> room.map(column -> TextWidth.lines(text, column.columns() - BORDERS, column.rows()))
                        .getOrElse(1))
                .getOrElse(0);
    }

    /**
     * The text area with the focus, {@code shown} rows tall, and on its right border how far down it is scrolled, as
     * the areas of the content panel show it.
     */
    private final class Barred implements Element {

        private final int shown;

        Barred(int shown) {
            this.shown = shown;
        }

        @Override
        public void render(Frame frame, Rect at, RenderContext context) {
            area.render(frame, at, context);
            RichTextState state = area.state();
            BorderBar.downwards(frame, at, state.contentHeight(), state.viewportHeight(), state.scrollRow());
        }

        @Override
        public Constraint constraint() {
            return Constraint.length(shown);
        }

        @Override
        public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
            return area.preferredSize(availableWidth, availableHeight, context);
        }
    }

    /**
     * A row of the log, in the style of {@code cssClass}, after the time its operation was done when it is the title
     * of one.
     */
    private record Row(Option<String> time, String text, String cssClass) {

        static Row of(String text, String cssClass) {
            return new Row(Option.none(), text, cssClass);
        }
    }

    /**
     * The rows all the log takes, {@code outcome} wrapped at {@code width} columns.
     */
    private record Wrapped(Outcome outcome, int width, List<Row> rows) {}

    /**
     * The room the panel is drawn in: under the content panel in a column {@code rows} tall, {@code columns} wide.
     */
    public record Room(int rows, int columns) {}
}
