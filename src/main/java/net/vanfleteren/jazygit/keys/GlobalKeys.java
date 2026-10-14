package net.vanfleteren.jazygit.keys;

import dev.tamboui.tui.event.KeyEvent;
import net.vanfleteren.jazygit.feature.theme.ThemeMsg;

/**
 * The keys that work wherever the focus is.
 */
final class GlobalKeys {

    private GlobalKeys() {
    }

    static Keymap register(Keymap keys) {
        return keys.global(new Keymap.Global.Documented(Keymap.HELP_KEY, "help.global.help"))
                .global(new Keymap.Global.Dispatching("ctrl+t", "help.global.theme", GlobalKeys::isThemeKey,
                        ThemeMsg.Requested::new))
                .global(new Keymap.Global.Documented("q", "help.global.quit"));
    }

    /** Whether {@code event} is the shortcut that asks which theme to use. */
    static boolean isThemeKey(KeyEvent event) {
        return event.hasCtrl() && event.isCharIgnoreCase('t');
    }
}
