package net.vanfleteren.jazygit.features.changes.panes.stash;

import dev.tamboui.toolkit.elements.Panel;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.core.repo.Stash;
import net.vanfleteren.jazygit.kernel.state.Focus;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.screen.LeftPane;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import net.vanfleteren.jazygit.kernel.ui.widgets.PaneKeys;

/**
 * Bottom-left panel listing the stashes, newest first. The highlighted stash is unstashed with {@code g} and deleted with {@code d};
 * Enter moves the focus to its changes.
 */
public final class StashPanel implements LeftPane {

    /**
     * The pane, as the focus is on it.
     */
    public static final PaneId PANE = PaneId.STASH;

    public static final String ID = PANE.id();

    private final LoadableList<List<Stash>> list;
    private final Keymap keymap = Keymap.standard();
    // Whether the files pane was locked to the stashes at the last render (ADR 149).
    private boolean locked;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public StashPanel(Consumer<Msg> dispatch) {
        this(dispatch, Theme.Highlights.DEFAULT);
    }

    /**
     * @param highlights how the highlighted stash is shown, also when the pane is not in use
     */
    public StashPanel(Consumer<Msg> dispatch, Theme.Highlights highlights) {
        list = new LoadableList<>(
                Messages.get("panel.stash.title"), ID, LoadableList.Reselect.following(Stash::sha), highlights);
        // Escape lets go of the files locked to the stashes, before it does anything else (ADR 149).
        list.onKey(event -> locked && event.isCancel()
                ? PaneKeys.sending(Option.of(new SelectionMsg.FilesUnlocked()), dispatch)
                : PaneKeys.sending(keymap.handle(Scopes.STASH, event, selectedStash()), dispatch));
    }

    /**
     * The text of a stash row: its position in the stash list, then what it was stashed as.
     */
    static String row(int index, Stash stash) {
        return index + " " + stash.message();
    }

    /**
     * The pane while {@code pane} has the focus, and is in use.
     */
    public Panel render(Model model, PaneId pane) {
        return render(model, Option.of(pane), Option.of(pane));
    }

    /**
     * @param focus what has the focus, if anything
     * @param inUse the left pane in use, whose highlighted row shows
     */
    @Override
    public Panel render(Model model, Option<Focus> focus, Option<PaneId> inUse) {
        locked = PaneKeys.lockedTo(model, PANE);
        return list.render(
                model.repo().stashes(),
                stashes -> stashes.zipWithIndex((stash, index) -> LoadableList.Row.of(row(index, stash))),
                focus.contains(PANE),
                inUse.contains(PANE));
    }

    /**
     * The highlighted stash, once the stashes are shown.
     */
    public Option<Stash> selectedStash() {
        return list.selected(stashes -> stashes);
    }

    /**
     * The id of the pane, by which its screen lists it.
     */
    @Override
    public PaneId pane() {
        return PANE;
    }

    /**
     * The message to send when the highlighted stash is not the one whose changes {@code model} holds.
     */
    @Override
    public Option<Msg> selectionChange(Model model) {
        return PaneKeys.selectionChange(
                selectedStash().map(Stash::sha),
                model.repoView().preview().stashDetail(),
                SelectionMsg.StashSelected::new);
    }
}
