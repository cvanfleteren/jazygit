package net.vanfleteren.jazygit.feature.stash;

import net.vanfleteren.jazygit.state.TextPopup;

/**
 * Asking for the name of the stash of the current changes.
 *
 * @param name the name as far as it was typed; a blank one asks git for a generated one
 */
public record StashPopup(String name) implements TextPopup {

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
}
