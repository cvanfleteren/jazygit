package net.vanfleteren.jazygit.ui;

import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Failure;

/**
 * What a list shows for data that is still loading or failed to load.
 */
final class Placeholders {

    private Placeholders() {
    }

    static String loading() {
        return Messages.get("placeholder.loading");
    }

    static String error(Failure failure) {
        return Messages.get("placeholder.error", ErrorText.of(failure));
    }
}
