package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.event.KeyEventHandler;
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
    private Loadable<T> shown;
    private List<Row> shownRows = List.of();
    private Option<Integer> count = Option.none();

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
            return detail.<StyledElement<?>>map(line -> column(first, dev.tamboui.toolkit.Toolkit.text(line).addClass(Theme.Classes.DIM)))
                    .getOrElse(first);
        }

        private StyledElement<?> firstLine() {
            return segs.size() == 1 && segs.head().cssClass().isEmpty()
                    ? dev.tamboui.toolkit.Toolkit.text(segs.head().text())
                    : row(segs
                            .<StyledElement<?>>map(seg -> seg.cssClass().<StyledElement<?>>map(c -> dev.tamboui.toolkit.Toolkit.text(seg.text()).addClass(c))
                                    .getOrElse(() -> dev.tamboui.toolkit.Toolkit.text(seg.text())))
                            .toJavaArray(StyledElement[]::new));
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
        List<Row> rows = switch (value) {
            case Loadable.Loading<T>() -> List.of(Row.of(Placeholders.loading()));
            case Loadable.Failed<T>(Failure failure) -> List.of(Row.of(Placeholders.error(failure)));
            case Loadable.Loaded<T>(T loaded) -> items.apply(loaded);
        };
        if (value != shown) {
            Loadable<T> previous = shown;
            shown = value;
            show(rows);
            if (previous instanceof Loadable.Loaded<T>(T before) && value instanceof Loadable.Loaded<T>(T after)) {
                list.selected(reselect.apply(before, after, selectedIndex()));
            }
            if (list.selected() >= rows.size()) {
                list.selected(Math.max(0, rows.size() - 1));
            }
            count = value instanceof Loadable.Loaded<T> ? Option.some(rows.size()) : Option.none();
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
     * The value shown by the last render, or {@code null} before the first one.
     */
    Loadable<T> shown() {
        return shown;
    }

    /**
     * Sets the text drawn before the highlighted row. The default one indents every row.
     */
    void highlightSymbol(String symbol) {
        list.highlightSymbol(symbol);
    }

    /**
     * Handles the keys the list itself does not use while it has focus; it only uses the
     * navigation keys.
     */
    void onKeyEvent(KeyEventHandler handler) {
        list.onKeyEvent(handler);
    }
}
