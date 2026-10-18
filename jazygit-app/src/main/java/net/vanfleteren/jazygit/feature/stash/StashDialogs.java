package net.vanfleteren.jazygit.feature.stash;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.FilesPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.StashPanel;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;
import net.vanfleteren.jazygit.ui.widgets.TextInputDialog;

/**
 * The popups of the stash operations.
 */
public final class StashDialogs {

    /**
     * Asks for the name of a stash; a blank name stashes under a generated one.
     */
    public static final TextInputDialog.Spec<StashPopup> STASH = new TextInputDialog.Spec<>(
            "stash-name",
            StashPopup.class,
            popup -> Messages.get("dialog.stash.title"),
            Option.of(Messages.get("dialog.stash.placeholder")),
            popup -> "",
            StashMsg.Confirmed::new,
            StashMsg.Cancelled::new);

    /**
     * Asks to drop a stash.
     */
    public static final ConfirmDialog.Spec<StashDropPopup> DROP = new ConfirmDialog.Spec<>(
            "stash-drop",
            StashDropPopup.class,
            popup -> Messages.get("dialog.stashDrop.title"),
            popup -> Messages.get("dialog.stashDrop.message", popup.stash().message()),
            StashMsg.DropConfirmed::new,
            StashMsg.DropCancelled::new);

    private StashDialogs() {}

    /**
     * The popups of this feature, which send what the user does to {@code dispatch}.
     */
    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningTo(StashPanel.ID, new ConfirmDialog<>(DROP, dispatch)),
                PopupSlot.returningTo(FilesPanel.ID, new TextInputDialog<>(STASH, dispatch)));
    }
}
