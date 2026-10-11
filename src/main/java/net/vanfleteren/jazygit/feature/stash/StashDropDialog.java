package net.vanfleteren.jazygit.feature.stash;

import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

import java.util.function.Consumer;

/**
 * Popup asking to confirm the deletion of a stash.
 */
public final class StashDropDialog {

    public static final String ID = "stash-drop";

    private StashDropDialog() {
    }

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public static ConfirmDialog<StashDropPopup> create(Consumer<Msg> dispatch) {
        return new ConfirmDialog<>(ID, StashDropPopup.class,
                popup -> Messages.get("dialog.stashDrop.title"),
                popup -> Messages.get("dialog.stashDrop.message"),
                StashMsg.DropConfirmed::new, StashMsg.DropCancelled::new, dispatch);
    }
}
