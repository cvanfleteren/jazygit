package net.vanfleteren.jazygit.feature.settings;

import net.vanfleteren.jazygit.state.TextPopup;

/**
 * Asking which gh executable to run.
 *
 * @param executable the executable as far as it was typed; it starts out as the one chosen now, empty for the one
 *                   on the PATH
 */
public record GhExecutablePopup(String executable) implements TextPopup {

    @Override
    public String text() {
        return executable;
    }

    @Override
    public GhExecutablePopup withText(String text) {
        return new GhExecutablePopup(text);
    }
}
