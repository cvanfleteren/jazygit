package net.vanfleteren.jazygit.features.chrome.error;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.popup.ErrorPopup;
import net.vanfleteren.jazygit.kernel.state.popup.PopupMsg;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.text.ErrorText;
import net.vanfleteren.jazygit.kernel.ui.widgets.ConfirmDialog;

/**
 * The popup telling what went wrong. Enter and Escape both close it.
 */
public final class ErrorDialog {

    public static final ConfirmDialog.Spec<ErrorPopup> SPEC = new ConfirmDialog.Spec<>(
            "error",
            ErrorPopup.class,
            popup -> Messages.get("chrome.dialog.error.title"),
            popup -> ErrorText.of(popup.error()),
            () -> new PopupMsg.Dismissed(ErrorPopup.class),
            () -> new PopupMsg.Dismissed(ErrorPopup.class),
            "changes.dialog.hint.close");

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
