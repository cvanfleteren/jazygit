package net.vanfleteren.jazygit.ui.widgets;

import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.bindings.Actions;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import io.vavr.collection.Iterator;
import io.vavr.control.Option;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * Where the keys and the mouse wheel move the highlight of a list whose highlighted line lives in the model: the
 * arrows to the next line that can be highlighted, Home and Page Up to the first, End and Page Down to the last, and
 * the wheel three lines at a time. Lines that cannot be highlighted, like headings, are skipped.
 */
public final class ListKeys {

    private static final int WHEEL_LINES = 3;

    private ListKeys() {}

    /**
     * The line the highlight moves to from {@code from} on {@code event}, among {@code size} lines of which
     * {@code highlightable} says which can be highlighted; none if it is not a key that moves it. At either end it
     * stays put.
     */
    public static Option<Integer> move(KeyEvent event, int from, int size, IntPredicate highlightable) {
        if (event.matches(Actions.MOVE_UP) || event.matches(Actions.MOVE_DOWN)) {
            return Option.of(step(from, event.matches(Actions.MOVE_UP) ? -1 : 1, size, highlightable));
        }
        if (event.matches(Actions.HOME) || event.matches(Actions.PAGE_UP)) {
            return Option.of(Iterator.range(0, size).find(highlightable::test).getOrElse(from));
        }
        if (event.matches(Actions.END) || event.matches(Actions.PAGE_DOWN)) {
            return Option.of(
                    Iterator.rangeBy(size - 1, -1, -1).find(highlightable::test).getOrElse(from));
        }
        return Option.none();
    }

    /**
     * What {@code event} means to {@code list}, whose filter {@code line} edits; none if nothing. Escape closes the
     * open filter, and otherwise cancels. {@code /} opens the filter when it is closed and {@code canFilter}. While it
     * is open, it takes the keys that edit it before the highlight does, so the arrows to the sides move in it.
     * Enter confirms.
     */
    static Option<ListKey> classify(KeyEvent event, FilteredList list, boolean canFilter, FilterLine line) {
        Option<String> typed = list.cursor().filter();
        if (event.code() == KeyCode.ESCAPE) {
            return Option.of(typed.isDefined() ? new ListKey.CloseFilter() : new ListKey.Cancel());
        }
        if (typed.isEmpty() && canFilter && ListFilter.opens(event)) {
            return Option.of(new ListKey.OpenFilter());
        }
        return typed.flatMap(text -> line.edit(text, event))
                .<ListKey>map(ListKey.EditFilter::new)
                .orElse(() -> move(event, list.selected(), list.size(), list::highlightable)
                        .map(ListKey.Move::new))
                .orElse(() -> Option.when(event.isConfirm(), ListKey.Confirm::new));
    }

    /**
     * Moves the highlight from {@code from} as the wheel turns in {@code event}, telling {@code moved} where to when
     * it moved.
     */
    public static EventResult wheel(
            MouseEvent event, int from, int size, IntPredicate highlightable, IntConsumer moved) {
        int direction =
                event.kind() == MouseEventKind.SCROLL_UP ? -1 : event.kind() == MouseEventKind.SCROLL_DOWN ? 1 : 0;
        if (direction == 0) {
            return EventResult.UNHANDLED;
        }
        int to = Iterator.iterate(from, line -> step(line, direction, size, highlightable))
                .drop(WHEEL_LINES)
                .head();
        if (to != from) {
            moved.accept(to);
        }
        return EventResult.HANDLED;
    }

    /**
     * The next line from {@code from} in {@code direction} that can be highlighted, or {@code from} when there is none.
     */
    private static int step(int from, int direction, int size, IntPredicate highlightable) {
        Iterator<Integer> next = direction < 0
                ? Iterator.iterate(from - 1, i -> i - 1).takeWhile(i -> i >= 0)
                : Iterator.iterate(from + 1, i -> i + 1).takeWhile(i -> i < size);
        return next.find(highlightable::test).getOrElse(from);
    }
}
