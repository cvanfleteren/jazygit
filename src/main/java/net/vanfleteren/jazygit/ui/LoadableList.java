package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.event.KeyEventHandler;
import net.vanfleteren.jazygit.state.Loadable;

import java.util.List;
import java.util.function.Function;

/**
 * A focusable list showing a {@link Loadable} value. The list widget keeps its own selection, so
 * its items are only rebuilt when the value changes.
 */
final class LoadableList<T> {

    private final String title;
    private final ListElement<?> list;
    private final Function<T, List<String>> items;
    private final Reselect<T> reselect;
    private Loadable<T> shown;
    private int count;

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

    LoadableList(String title, String id, Function<T, List<String>> items) {
        this(title, id, items, (previous, next, selected) -> selected);
    }

    LoadableList(String title, String id, Function<T, List<String>> items, Reselect<T> reselect) {
        this.title = title;
        this.items = items;
        this.reselect = reselect;
        this.list = list()
                .id(id)
                .focusable();
    }

    Panel render(Loadable<T> value, boolean focused) {
        if (value != shown) {
            Loadable<T> previous = shown;
            shown = value;
            List<String> rows = Placeholders.items(value, items);
            list.items(rows);
            if (previous instanceof Loadable.Loaded<T>(T before) && value instanceof Loadable.Loaded<T>(T after)) {
                list.selected(reselect.apply(before, after, selectedIndex()));
            }
            if (list.selected() >= rows.size()) {
                list.selected(Math.max(0, rows.size() - 1));
            }
            count = value instanceof Loadable.Loaded<T> ? rows.size() : 0;
        }
        return Pane.bordered(title, list, focused, selectedIndex(), count);
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
     * Handles the keys the list itself does not use while it has focus; it only uses the
     * navigation keys.
     */
    void onKeyEvent(KeyEventHandler handler) {
        list.onKeyEvent(handler);
    }
}
