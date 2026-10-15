package net.vanfleteren.jazygit.feature.error;

import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.ErrorPopup;
import net.vanfleteren.jazygit.ui.ErrorText;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

/**
 * The popup telling what went wrong. Enter and Escape both close it.
 */
public final class ErrorDialog {

    public static final ConfirmDialog.Spec<ErrorPopup> SPEC = new ConfirmDialog.Spec<>(
            "error",
            ErrorPopup.class,
            popup -> Messages.get("dialog.error.title"),
            popup -> ErrorText.of(popup.error()),
            ErrorMsg.Dismissed::new,
            ErrorMsg.Dismissed::new,
            "dialog.hint.close");

    private ErrorDialog() {}
}
