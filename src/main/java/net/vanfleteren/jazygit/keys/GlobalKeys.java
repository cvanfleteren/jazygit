package net.vanfleteren.jazygit.keys;

import net.vanfleteren.jazygit.ui.Theme;

/**
 * The keys that work wherever the focus is.
 */
final class GlobalKeys {

    private GlobalKeys() {
    }

    static void register(Keymap.Builder keys) {
        keys.global(Keymap.Global.documented("?", "help.global.help"))
                .global(new Keymap.Global("ctrl+t", "help.global.theme", Theme::isToggleKey, Theme::toggle))
                .global(Keymap.Global.documented("q", "help.global.quit"));
    }
}
