package net.vanfleteren.jazygit.feature.error;

import net.vanfleteren.jazygit.state.ErrorPopup;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Closing the error popup. The error stays in the command log until the next operation starts.
 */
public final class ErrorUpdate {

    private ErrorUpdate() {
    }

    public static Next update(Model model, ErrorMsg msg) {
        return switch (msg) {
            case ErrorMsg.Dismissed() -> Next.of(model.withoutPopup(ErrorPopup.class));
        };
    }
}
