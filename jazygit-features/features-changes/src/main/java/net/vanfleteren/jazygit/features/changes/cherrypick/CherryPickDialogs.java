package net.vanfleteren.jazygit.features.changes.cherrypick;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.widgets.ConfirmDialog;

/**
 * The confirmation of a cherry-pick.
 */
public final class CherryPickDialogs {

    /**
     * Asks to cherry-pick the copied commit onto the checked out branch.
     */
    public static final ConfirmDialog.Spec<CherryPickPopup> PICK = ConfirmDialog.Spec.confirming(
            "cherry-pick",
            CherryPickPopup.class,
            popup -> Messages.get("changes.dialog.cherryPick.title"),
            popup -> popup.onto()
                    .map(branch -> Messages.get(
                            "changes.dialog.cherryPick.message", popup.commit().shortSha(), branch.value()))
                    .getOrElse(() -> Messages.get(
                            "changes.dialog.cherryPick.messageDetached",
                            popup.commit().shortSha())));

    private CherryPickDialogs() {}

    /**
     * The popups of this feature, which send what the user does to {@code dispatch}.
     */
    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(PaneId.COMMITS, new ConfirmDialog<>(PICK, dispatch)));
    }
}
