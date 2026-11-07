package net.vanfleteren.jazygit.feature.settings;

import net.vanfleteren.jazygit.gh.model.GhAccount;
import net.vanfleteren.jazygit.state.TextPopup;

/**
 * Asking which browser links open in for the account the repository uses (ADR 88).
 *
 * @param account the account, on its host with an ssh alias resolved
 * @param browser the browser as far as it was typed; it starts out as the one set now, empty for none
 */
public record AccountBrowserPopup(GhAccount account, String browser) implements TextPopup {

    @Override
    public String text() {
        return browser;
    }

    @Override
    public AccountBrowserPopup withText(String text) {
        return new AccountBrowserPopup(account, text);
    }
}
