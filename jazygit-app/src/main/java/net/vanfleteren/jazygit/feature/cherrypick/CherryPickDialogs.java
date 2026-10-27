package net.vanfleteren.jazygit.feature.cherrypick;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.CommitsPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

/**
 * The confirmation of a cherry-pick.
 */
public final class CherryPickDialogs {

    /**
     * Asks to cherry-pick the copied commit onto the checked out branch.
     */
    public static final ConfirmDialog.Spec<CherryPickPopup> PICK = new ConfirmDialog.Spec<>(
            "cherry-pick",
            CherryPickPopup.class,
            popup -> Messages.get("dialog.cherryPick.title"),
            popup -> popup.onto()
                    .map(branch -> Messages.get(
                            "dialog.cherryPick.message", popup.commit().shortSha(), branch.value()))
                    .getOrElse(() -> Messages.get(
                            "dialog.cherryPick.messageDetached", popup.commit().shortSha())),
            CherryPickMsg.Confirmed::new);

    private CherryPickDialogs() {}

    /**
     * The popups of this feature, which send what the user does to {@code dispatch}.
     */
    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(CommitsPanel.ID, new ConfirmDialog<>(PICK, dispatch)));
    }
}
