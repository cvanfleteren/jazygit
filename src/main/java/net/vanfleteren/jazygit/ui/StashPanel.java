package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.elements.Panel;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;

import java.util.function.Consumer;

/**
 * Bottom-left panel listing the stashes, newest first. The highlighted stash is unstashed with {@code g} and deleted with {@code d}.
 */
public final class StashPanel {

    public static final String ID = Scopes.STASH.id();

    private final LoadableList<List<Stash>> list = new LoadableList<>(Messages.get("panel.stash.title"), ID,
            stashes -> stashes.zipWithIndex((stash, index) -> LoadableList.Row.of(row(index, stash))),
            LoadableList.Reselect.following(Stash::sha));
    private final Keymap keymap = Keymap.standard();

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public StashPanel(Consumer<Msg> dispatch) {
        list.onKey(event -> PaneKeys.sending(keymap.handle(Scopes.STASH, event, selectedStash()), dispatch));
    }

    /**
     * @param focusedId the id of the currently focused left pane, as reported by
     *                  {@code runner().focusManager().focusedId()}
     */
    public Panel render(Model model, String focusedId) {
        return list.render(model.stashes(), ID.equals(focusedId));
    }

    /**
     * The highlighted stash, once the stashes are shown.
     */
    public Option<Stash> selectedStash() {
        return list.selected(stashes -> stashes);
    }

    /**
     * The text of a stash row: its position in the stash list, then what it was stashed as.
     */
    static String row(int index, Stash stash) {
        return index + " " + stash.message();
    }

}
