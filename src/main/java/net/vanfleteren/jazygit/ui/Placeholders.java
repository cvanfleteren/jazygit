package net.vanfleteren.jazygit.ui;

import net.vanfleteren.jazygit.i18n.Messages;

/**
 * What a list shows for data that is still loading or failed to load.
 */
final class Placeholders {

    static String loading() {
        return Messages.get("placeholder.loading");
    }

    private Placeholders() {
    }

    static String error(String message) {
        return Messages.get("placeholder.error", message);
    }
}
