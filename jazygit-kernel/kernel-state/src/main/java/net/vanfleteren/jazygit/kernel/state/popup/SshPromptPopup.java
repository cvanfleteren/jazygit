package net.vanfleteren.jazygit.kernel.state.popup;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Model;

/**
 * ssh asking the user for a secret, such as the passphrase of a key, while an operation the user started talks to a
 * remote (ADR 150). What is typed is not here: only the app holds it, outside the model. The popup that was open
 * stays under it, and comes back once it closes; one that opens meanwhile goes under it too.
 *
 * @param request     the question, as the app numbers it
 * @param prompt      what ssh asks, as it asks it
 * @param under       the popup that was open, if any
 * @param underCursor where the user was in the list of that popup
 */
public record SshPromptPopup(long request, String prompt, Option<Popup> under, ListCursor underCursor)
        implements Popup {

    /**
     * This popup, with {@code popup} under it in place of the one that was, from the top of its list.
     */
    public SshPromptPopup over(Popup popup) {
        return new SshPromptPopup(request, prompt, Option.of(popup), ListCursor.START);
    }

    /**
     * This popup, without the one under it if that is a {@code type}.
     */
    public SshPromptPopup without(Class<? extends Popup> type) {
        return under.exists(type::isInstance)
                ? new SshPromptPopup(request, prompt, Option.none(), ListCursor.START)
                : this;
    }

    @Override
    public boolean holdsInput() {
        return under.exists(Popup::holdsInput);
    }

    @Override
    public Popup checkTimesChanging(Model before) {
        return new SshPromptPopup(request, prompt, under.map(popup -> popup.checkTimesChanging(before)), underCursor);
    }
}
