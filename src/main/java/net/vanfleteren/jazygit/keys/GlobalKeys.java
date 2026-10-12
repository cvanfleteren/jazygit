package net.vanfleteren.jazygit.keys;

import net.vanfleteren.jazygit.feature.theme.ThemeMsg;
import net.vanfleteren.jazygit.ui.Theme;

/**
 * The keys that work wherever the focus is.
 */
final class GlobalKeys {

    private GlobalKeys() {
    }

    static Keymap register(Keymap keys) {
        return keys.global(new Keymap.Global.Documented("?", "help.global.help"))
                .global(new Keymap.Global.Dispatching("ctrl+t", "help.global.theme", Theme::isThemeKey,
                        ThemeMsg.Requested::new))
                .global(new Keymap.Global.Documented("q", "help.global.quit"));
    }
}
