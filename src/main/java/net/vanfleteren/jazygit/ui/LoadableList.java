package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.text.Line;
import dev.tamboui.toolkit.Toolkit;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.common.ScrollBarPolicy;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.ui.widgets.Pane;

import java.util.function.Function;

/**
 * A focusable list showing a {@link Loadable} value. The list widget keeps its own selection, so
 * its items are only rebuilt when the value changes.
 */
final class LoadableList<T> {

    private final String title;
    private final ListElement<?> list;
    private final Function<T, List<Row>> items;
    private final Reselect<T> reselect;
    // The value the last render showed, and its rows.
    private Option<Loadable<T>> shown = Option.none();
    private List<Row> shownRows = List.of();
    private Option<Integer> count = Option.none();
    private Option<String> empty = Option.none();
    private boolean focused;

    /**
     * A piece of a row, in a colour of its own when it has one.
     */
    record Seg(String text, Option<String> cssClass) {

        static Seg of(String text) {
            return new Seg(text, Option.none());
        }

        static Seg styled(String text, String cssClass) {
            return new Seg(text, Option.of(cssClass));
        }
    }

    /**
     * A row of the list: consecutive pieces, each with its own colour.
     */
    record Row(List<Seg> segs, Option<String> detail) {

        Row(List<Seg> segs) {
            this(segs, Option.none());
        }

        static Row of(String text) {
            return new Row(List.of(Seg.of(text)));
        }

        static Row of(Seg... segs) {
            return new Row(List.of(segs));
        }

        /**
         * This row with a dimmed line below it, still one item of the list.
         */
        Row withDetail(String line) {
            return new Row(segs, Option.of(line));
        }

        String text() {
            return segs.map(Seg::text).mkString();
        }

        StyledElement<?> element() {
            StyledElement<?> first = firstLine();
            return detail.<StyledElement<?>>map(line -> column(first, Toolkit.text(line).addClass(Theme.Classes.DIM)))
                    .getOrElse(first);
        }

        private StyledElement<?> firstLine() {
            List<StyledElement<?>> pieces = segs.map(seg -> seg.cssClass()
                    .<StyledElement<?>>map(cssClass -> Toolkit.text(seg.text()).addClass(cssClass))
                    .getOrElse(() -> Toolkit.text(seg.text())));
            return pieces.size() == 1 ? pieces.head() : row(pieces.toJavaArray(StyledElement[]::new));
        }
    }

    /**
     * Where the selection goes when the loaded value changes.
     */
    @FunctionalInterface
    interface Reselect<T> {

        /**
         * @param selected the index selected in {@code previous}
         * @return the index to select in {@code next}
         */
        int apply(T previous, T next, int selected);

        /**
         * The selection follows its item, as {@code key} identifies it, when items are added or removed before
         * it; when it is gone, the selection stays where it was.
         */
        static <E, K> Reselect<List<E>> following(Function<E, K> key) {
            return (previous, next, selected) -> previous.lift().apply(selected)
                    .map(key)
                    .map(item -> next.indexWhere(candidate -> key.apply(candidate).equals(item)))
                    .filter(index -> index >= 0)
                    .getOrElse(selected);
        }
    }

    LoadableList(String title, String id, Function<T, List<Row>> items) {
        this(title, id, items, (previous, next, selected) -> selected);
    }

    LoadableList(String title, String id, Function<T, List<Row>> items, Reselect<T> reselect) {
        this.title = title;
        this.items = items;
        this.reselect = reselect;
        this.list = list()
                .id(id)
                .focusable()
                .autoScroll()
                .scrollbar(ScrollBarPolicy.AS_NEEDED)
                .fill();
    }

    Panel render(Loadable<T> value, boolean focused) {
        return render(value, focused, Pane.title(title));
    }

    /**
     * @param title the title to show instead of the one the list was made with
     */
    Panel render(Loadable<T> value, boolean focused, Line title) {
        this.focused = focused;
        List<Row> items = value.toOption().map(this.items).getOrElse(List.empty());
        List<Row> rows = switch (value) {
            case Loadable.Loading<T>() -> List.of(Row.of(Placeholders.loading()));
            case Loadable.Failed<T>(Failure failure) -> List.of(Row.of(Placeholders.error(failure)));
            case Loadable.Loaded<T>(T loaded) -> items.isEmpty()
                    ? empty.map(text -> List.of(Row.of(Seg.styled(text, Theme.Classes.DIM)))).getOrElse(items)
                    : items;
        };
        if (!shown.exists(previous -> previous == value)) {
            Option<T> before = shown.flatMap(Loadable::toOption);
            shown = Option.of(value);
            show(rows);
            before.forEach(previous -> value.toOption()
                    .forEach(after -> list.selected(reselect.apply(previous, after, selectedIndex()))));
            if (list.selected() >= rows.size()) {
                list.selected(Math.max(0, rows.size() - 1));
            }
            count = value.toOption().map(loaded -> items.size());
        } else if (!rows.equals(shownRows)) {
            // The rows can depend on more than the value, e.g. on the time.
            show(rows);
        }
        shownRows = rows;
        return Pane.bordered(title, list, focused, selectedIndex(), count);
    }

    private void show(List<Row> rows) {
        list.elements(rows.map(Row::element).toJavaArray(StyledElement[]::new));
    }

    /**
     * The index of the currently highlighted row, never negative.
     */
    int selectedIndex() {
        return Math.max(0, list.selected());
    }

    /**
     * The item of the highlighted row, once the value is shown; {@code items} are the items of the value, one
     * per row.
     */
    <E> Option<E> selected(Function<T, List<E>> items) {
        return shown.flatMap(Loadable::toOption)
                .map(items)
                .flatMap(all -> all.lift().apply(Math.min(selectedIndex(), all.size() - 1)));
    }

    /**
     * Whether the list had the focus at the last render.
     */
    boolean focused() {
        return focused;
    }

    /**
     * Sets the dimmed line shown instead of the rows when the loaded value has none.
     */
    void whenEmpty(String text) {
        empty = Option.of(text);
    }

    /**
     * Sets the text drawn before the highlighted row. The default one indents every row.
     */
    void highlightSymbol(String symbol) {
        list.highlightSymbol(symbol);
    }

    /**
     * Does what {@code action} makes of the keys the list itself does not use, while it has the focus; it
     * only uses the navigation keys.
     */
    void onKey(Function<KeyEvent, Option<Runnable>> action) {
        list.onKeyEvent(PaneKeys.whileFocused(this::focused, action));
    }
}
