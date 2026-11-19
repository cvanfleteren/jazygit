package net.vanfleteren.jazygit.features.changes.stash;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.popup.ConfirmablePopup;
import net.vanfleteren.jazygit.kernel.state.popup.TextPopup;

/**
 * Asking for the name of the stash of the current changes.
 *
 * @param name the name as far as it was typed; a blank one asks git for a generated one
 */
public record StashPopup(String name) implements TextPopup, ConfirmablePopup {

    public StashPopup() {
        this("");
    }

    @Override
    public String text() {
        return name;
    }

    @Override
    public StashPopup withText(String text) {
        return new StashPopup(text);
    }

    @Override
    public Option<StashCmd.Stash> confirm() {
        return Option.of(new StashCmd.Stash(Option.of(name.strip()).filter(text -> !text.isEmpty())));
    }
}
