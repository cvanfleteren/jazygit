package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.style.Color;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.event.KeyEventHandler;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.ui.widgets.Pane;

import java.util.List;
import java.util.Optional;
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
    private int count;

    /**
     * A piece of a row, in a colour of its own when it has one.
     */
    record Seg(String text, Optional<Color> color) {

        static Seg of(String text) {
            return new Seg(text, Optional.empty());
        }

        static Seg colored(String text, Color color) {
            return new Seg(text, Optional.of(color));
        }
    }

    /**
     * A row of the list: consecutive pieces, each with its own colour.
     */
    record Row(List<Seg> segs) {

        static Row of(String text) {
            return new Row(List.of(Seg.of(text)));
        }

        static Row of(Seg... segs) {
            return new Row(List.of(segs));
        }

        String text() {
            return segs.stream().map(Seg::text).collect(java.util.stream.Collectors.joining());
        }

        StyledElement<?> element() {
            return segs.size() == 1 && segs.getFirst().color().isEmpty()
                    ? dev.tamboui.toolkit.Toolkit.text(segs.getFirst().text())
                    : row(segs.stream()
                            .map(seg -> seg.color().map(c -> dev.tamboui.toolkit.Toolkit.text(seg.text()).fg(c))
                                    .orElseGet(() -> dev.tamboui.toolkit.Toolkit.text(seg.text())))
                            .toArray(StyledElement[]::new));
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
                .focusable();
    }

    Panel render(Loadable<T> value, boolean focused) {
        List<Row> rows = switch (value) {
            case Loadable.Loading<T>() -> List.of(Row.of(Placeholders.loading()));
            case Loadable.Failed<T>(String message) -> List.of(Row.of(Placeholders.error(message)));
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
            count = value instanceof Loadable.Loaded<T> ? rows.size() : 0;
        } else if (!rows.equals(shownRows)) {
            // The rows can depend on more than the value, e.g. on the time.
            show(rows);
        }
        shownRows = rows;
        return Pane.bordered(title, list, focused, selectedIndex(), count);
    }

    private void show(List<Row> rows) {
        list.elements(rows.stream().map(Row::element).toArray(StyledElement[]::new));
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
