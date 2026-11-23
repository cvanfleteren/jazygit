package net.vanfleteren.jazygit.kernel.ui.tabs;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.view.TabId;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;

/**
 * A tab a feature adds to a pane that declares a key for its tabs (ADR 185): all the pane needs to show it, as the
 * feature makes it of a model. The pane knows nothing of what it lists.
 * <p>
 * Its scope hosts {@link net.vanfleteren.jazygit.kernel.ui.keys.Scopes#PANE_TABS}, as the feature declares it, so that
 * the keys that switch tabs work from it too.
 *
 * @param id        the id of the tab, the same for every model: the pane keeps which tab it shows by it
 * @param title     its name in the title of the pane
 * @param scope     the scope of the keys of its rows, which act on the highlighted one
 * @param rows      what it lists
 * @param item      the row of each
 * @param emptyText what it says when it lists nothing, if anything
 * @param selected  the message to send when the row highlighted is the one given, or none, if the model already holds
 *                  what is shown for it
 * @param <T>       what it lists
 */
public record PaneTab<T>(
        TabId id,
        String title,
        Keymap.Scope<T> scope,
        Loadable<List<T>> rows,
        Function<T, LoadableList.Item> item,
        Option<String> emptyText,
        Function<Option<T>, Option<Msg>> selected) {}
