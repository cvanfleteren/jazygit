package net.vanfleteren.jazygit.ui.text;

import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.ui.i18n.Messages;

/**
 * What a list shows for data that is still loading or failed to load.
 */
public final class Placeholders {

    private Placeholders() {}

    public static String loading() {
        return Messages.get("placeholder.loading");
    }

    public static String error(Failure failure) {
        return Messages.get("placeholder.error", ErrorText.of(failure));
    }
}
