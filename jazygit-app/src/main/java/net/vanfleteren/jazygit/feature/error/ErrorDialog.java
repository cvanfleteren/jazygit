package net.vanfleteren.jazygit.feature.error;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.ErrorPopup;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.ErrorText;
import net.vanfleteren.jazygit.ui.PopupSlot;
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

    /**
     * The popups of this feature, which send what the user does to {@code dispatch}.
     */
    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                // Errors come up wherever the user is.
                PopupSlot.returningToFocus(new ConfirmDialog<>(SPEC, dispatch)));
    }
}
