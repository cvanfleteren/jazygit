package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.SelectionMsg;

/**
 * Bottom-left panel listing the stashes, newest first. The highlighted stash is unstashed with {@code g} and deleted with {@code d};
 * Enter moves the focus to its changes.
 */
public final class StashPanel {

    public static final String ID = Scopes.STASH.id();

    private final LoadableList<List<Stash>> list;
    private final Keymap keymap = Keymap.standard();

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public StashPanel(Consumer<Msg> dispatch) {
        this(dispatch, () -> {}, Theme.Highlights.DEFAULT);
    }

    /**
     * @param openStash  called when Enter is pressed on a stash, to move focus to its changes
     * @param highlights how the highlighted stash is shown, also when the pane is not in use
     */
    public StashPanel(Consumer<Msg> dispatch, Runnable openStash, Theme.Highlights highlights) {
        list = new LoadableList<>(
                Messages.get("panel.stash.title"), ID, LoadableList.Reselect.following(Stash::sha), highlights);
        list.onKey(event -> event.code() == KeyCode.ENTER && selectedStash().isDefined()
                ? Option.of(openStash)
                : PaneKeys.sending(keymap.handle(Scopes.STASH, event, selectedStash()), dispatch));
    }

    /**
     * The text of a stash row: its position in the stash list, then what it was stashed as.
     */
    static String row(int index, Stash stash) {
        return index + " " + stash.message();
    }

    /**
     * @param focusedId the id of the currently focused left pane, as reported by
     *                  {@code runner().focusManager().focusedId()}
     */
    public Panel render(Model model, String focusedId) {
        return render(model, focusedId, focusedId);
    }

    /**
     * @param activeId the id of the left pane in use, whose highlighted row shows
     */
    public Panel render(Model model, String focusedId, String activeId) {
        return list.render(
                model.repo().stashes(),
                stashes -> stashes.zipWithIndex((stash, index) -> LoadableList.Row.of(row(index, stash))),
                ID.equals(focusedId),
                ID.equals(activeId));
    }

    /**
     * The highlighted stash, once the stashes are shown.
     */
    public Option<Stash> selectedStash() {
        return list.selected(stashes -> stashes);
    }

    /**
     * The message to send when the highlighted stash is not the one whose changes {@code model} holds.
     */
    public Option<Msg> selectionChange(Model model) {
        return PaneKeys.selectionChange(
                selectedStash().map(Stash::sha), model.repoView().stashDetail(), SelectionMsg.StashSelected::new);
    }
}
