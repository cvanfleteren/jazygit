package net.vanfleteren.jazygit.keys;

import dev.tamboui.tui.event.KeyEvent;
import net.vanfleteren.jazygit.feature.help.HelpMsg;
import net.vanfleteren.jazygit.feature.repos.RecentReposMsg;
import net.vanfleteren.jazygit.feature.settings.SettingsPanelMsg;
import net.vanfleteren.jazygit.state.Msg;

/**
 * The keys that work wherever the focus is.
 */
final class GlobalKeys {

    private GlobalKeys() {}

    static Keymap register(Keymap keys) {
        // The panels show their help for ? themselves; run from the help, it closes it, as pressing it there does.
        return keys.global(new Keymap.Global.Dispatching(
                        Keymap.HELP_KEY, "help.global.help", event -> false, HelpMsg.Closed::new))
                .global(new Keymap.Global.Dispatching(
                        "ctrl+r",
                        "help.global.recentRepos",
                        GlobalKeys::isRecentReposKey,
                        RecentReposMsg.Requested::new))
                .global(new Keymap.Global.Dispatching(
                        "ctrl+s", "help.global.settings", GlobalKeys::isSettingsKey, SettingsPanelMsg.Requested::new))
                .global(new Keymap.Global.Dispatching("q", "help.global.quit", GlobalKeys::isQuitKey, Msg.Quit::new));
    }

    /**
     * Whether {@code event} is the key that quits.
     */
    static boolean isQuitKey(KeyEvent event) {
        return event.isChar('q') && !event.hasCtrl() && !event.hasAlt();
    }

    /**
     * Whether {@code event} is the shortcut that shows the settings.
     */
    static boolean isSettingsKey(KeyEvent event) {
        return event.hasCtrl() && event.isCharIgnoreCase('s');
    }

    /**
     * Whether {@code event} is the shortcut that asks which recent repository to switch to.
     */
    static boolean isRecentReposKey(KeyEvent event) {
        return event.hasCtrl() && event.isCharIgnoreCase('r');
    }
}
