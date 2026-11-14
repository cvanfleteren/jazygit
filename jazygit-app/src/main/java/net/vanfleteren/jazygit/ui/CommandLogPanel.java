package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.style.Overflow;
import dev.tamboui.text.CharWidth;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Panel;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.ui.text.ErrorText;
import net.vanfleteren.jazygit.ui.text.NoticeText;
import net.vanfleteren.jazygit.ui.theme.Theme;

/**
 * Right-hand panel under the content panel, logging the mutating git commands that were executed, each
 * under a title, followed by why the last operation failed, when it did, or by a notice about it. It is
 * display-only and does not take part in focus cycling.
 *
 * <p>It is as tall as its lines, up to {@link #VISIBLE_LINES}, and the error or notice as it wraps: empty, it is its
 * borders and title alone. The content panel above it comes first: the log takes only the rows the content panel can
 * spare above {@link #CONTENT_MIN_ROWS}, and no fewer than its borders. While the content panel is maximized, by
 * collapsing the left column (ADR 104), the log shows the error or notice alone.
 */
public final class CommandLogPanel {

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

    private CommandLogPanel() {}

    /**
     * How many rows the panel takes in {@code room}, when that is known: its lines and borders, as many lines as the
     * content panel can spare above {@link #CONTENT_MIN_ROWS}, but at least its borders. The error or notice takes the
     * rows it wraps onto, even past {@link #VISIBLE_LINES}.
     */
    public static int height(Model model, Option<Room> room) {
        int outcome = outcomeRows(model, room);
        int wanted = BORDERS
                + Math.max(outcome, Math.min(VISIBLE_LINES, logLines(model).size() + outcome));
        int spare = room.map(column -> column.rows() - CONTENT_MIN_ROWS).getOrElse(wanted);
        return Math.max(BORDERS, Math.min(wanted, spare));
    }

    /**
     * The panel {@code rows} tall in {@code room}: the newest lines that fit in it above the error or notice, which
     * is kept out of the cut, and shows while a line fits.
     */
    public static Panel render(Model model, int rows, Option<Room> room) {
        int lines = Math.max(0, rows - BORDERS);
        Option<Element> outcome = outcome(model).filter(line -> lines > 0);
        List<Element> shown = logLines(model)
                .takeRight(Math.max(0, lines - outcomeRows(model, room)))
                .appendAll(outcome);
        return panel(Messages.get("panel.commandLog.title"), shown.toJavaArray(Element[]::new));
    }

    /**
     * The lines of the commands logged, each under the title of its operation; none while the content panel is
     * maximized, which collapses the left column of the screen shown (ADR 104).
     */
    private static List<Element> logLines(Model model) {
        if (model.preferences().leftCollapsed(model.screen())) {
            return List.empty();
        }
        return model.outcome()
                .commandLog()
                .flatMap(entry -> List.<Element>of(
                                text(Messages.get(entry.operation().logTitleKey()))
                                        .addClass(Theme.Classes.BOLD))
                        .appendAll(entry.commands()
                                .map(command -> text(INDENT + command.line()).addClass(Theme.Classes.DIM))));
    }

    /**
     * Why the last operation failed, when it did, or else a notice about it, wrapped at words.
     */
    private static Option<Element> outcome(Model model) {
        return outcomeText(model)
                .map(text -> text(text)
                        .addClass(model.outcome().error().isDefined() ? Theme.Classes.ERROR : Theme.Classes.NOTICE))
                .map(line -> line.overflow(Overflow.WRAP_WORD));
    }

    private static Option<String> outcomeText(Model model) {
        return model.outcome()
                .error()
                .map(ErrorText::of)
                .orElse(() -> model.outcome().notice().map(NoticeText::of));
    }

    /**
     * How many rows the error or notice takes, wrapped at words in the panel in {@code room}; one row when the room is
     * not known, and none without one.
     */
    private static int outcomeRows(Model model, Option<Room> room) {
        return outcomeText(model)
                .map(text -> room.map(column -> wrappedRows(text, column.columns() - BORDERS))
                        .getOrElse(1))
                .getOrElse(0);
    }

    /**
     * How many rows {@code text} takes wrapped at words, each line of it starting a row, in {@code width} columns: a
     * word goes on the next row when it does not fit after the one before, and a word wider than a row is cut.
     */
    static int wrappedRows(String text, int width) {
        if (width <= 0) {
            return 1;
        }
        return List.of(text.split("\n", -1))
                .map(line -> List.of(line.split(" "))
                        .filter(word -> !word.isEmpty())
                        .foldLeft(Tuple.of(1, 0), (at, word) -> placed(at, CharWidth.of(word), width))
                        ._1)
                .sum()
                .intValue();
    }

    /**
     * The rows taken, and the columns taken on the last of them, {@code at} with a word {@code wide} columns placed
     * after them in rows {@code width} columns wide.
     */
    private static Tuple2<Integer, Integer> placed(Tuple2<Integer, Integer> at, int wide, int width) {
        if (at._2 > 0 && at._2 + 1 + wide <= width) {
            return Tuple.of(at._1, at._2 + 1 + wide);
        }
        int row = at._2 > 0 ? at._1 + 1 : at._1;
        return Tuple.of(row + Math.max(0, wide - 1) / width, wide == 0 ? 0 : (wide - 1) % width + 1);
    }

    /**
     * The room the panel is drawn in: under the content panel in a column {@code rows} tall, {@code columns} wide.
     */
    public record Room(int rows, int columns) {}
}
