package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;

/**
 * A popup that asks about one operation, and confirming it only starts that: the dialogs send a
 * {@link Msg.PopupConfirmed} for it, which needs no message of the feature (ADR 38).
 */
public interface ConfirmablePopup extends Popup {

    /**
     * The operation this popup asks about, as it stands; none when what was entered cannot be used, which keeps
     * the popup open.
     */
    Option<? extends OperationCmd> confirm();
}
