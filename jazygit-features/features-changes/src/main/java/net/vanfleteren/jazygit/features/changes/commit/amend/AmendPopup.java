package net.vanfleteren.jazygit.features.changes.commit.amend;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.features.changes.commit.PushedWarning;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking to confirm amending {@code target} with the staged changes.
 *
 * @param warning that the commit of {@code target} is pushed already, if it is
 */
public record AmendPopup(AmendTarget target, Option<PushedWarning> warning) implements Popup {

    /**
     * Amending a commit that is not pushed.
     */
    public AmendPopup(AmendTarget target) {
        this(target, Option.none());
    }
}
