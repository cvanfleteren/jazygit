package net.vanfleteren.jazygit.kernel.ui.tabs;

import dev.tamboui.text.Line;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;

/**
 * The list of a tab a feature adds to a pane (ADR 185), drawn as the pane draws its own lists, with the id of the pane:
 * only the list shown is rendered, so only it gets keys.
 *
 * @param <T> what the tab lists
 */
public final class TabList<T> {

    private final LoadableList<List<T>> list;
    // The tab the last render showed.
    private Option<PaneTab<T>> shown = Option.none();

    /**
     * @param id the id of the pane, which has the focus
     */
    public TabList(String id, Theme.Highlights highlights) {
        list = new LoadableList<>("", id, this::reselect, highlights);
    }

    /**
     * Where the highlight goes as the rows of the tab shown change: to the row it was on, as the tab knows its rows.
     */
    private int reselect(List<T> previous, List<T> next, int selected) {
        return shown.map(tab -> following(tab.key(), previous, next, selected)).getOrElse(selected);
    }

    private static <T, K> int following(Function<T, K> key, List<T> previous, List<T> next, int selected) {
        return LoadableList.Reselect.following(key).apply(previous, next, selected);
    }

    /**
     * The pane showing {@code tab}, under {@code title}.
     *
     * @param focused whether the pane has the focus
     * @param active  whether it is the pane in use, which shows the highlighted row
     */
    public Panel render(PaneTab<T> tab, boolean focused, boolean active, Line title) {
        shown = Option.of(tab);
        list.whenEmpty(tab.emptyText());
        return list.renderItems(
                tab.rows(), rows -> rows.map(tab.item()), focused, active, title, Option.none(), paste -> {});
    }

    /**
     * Does what {@code action} makes of the keys the list itself does not use, while it has the focus.
     */
    public void onKey(Function<KeyEvent, Option<Runnable>> action) {
        list.onKey(action);
    }

    /**
     * What the keys of the tab shown do in {@code event}, on the highlighted row.
     *
     * @param hosted what the scopes the tab's scope hosts act on (ADR 161), as the tabs of the pane
     */
    public Option<Msg> keyed(Keymap keymap, KeyEvent event, Keymap.Hosted<?>... hosted) {
        return shown.flatMap(tab -> keymap.handle(tab.scope(), event, selected(), hosted));
    }

    /**
     * The highlighted row, once the rows are shown.
     */
    public Option<T> selected() {
        return list.selected(rows -> rows);
    }

    /**
     * The message the tab shown sends for the highlighted row, if the model does not hold what is shown for it yet.
     */
    public Option<Msg> selectionChange() {
        return shown.flatMap(tab -> tab.selected().apply(selected()));
    }

    /**
     * The rows the last render showed.
     */
    public List<LoadableList.Row> shownRows() {
        return list.shownRows();
    }
}
