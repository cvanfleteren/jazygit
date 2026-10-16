package net.vanfleteren.jazygit.feature.cherrypick;

import net.vanfleteren.jazygit.i18n.Messages;
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
            CherryPickMsg.Confirmed::new,
            CherryPickMsg.Cancelled::new);

    private CherryPickDialogs() {}
}
