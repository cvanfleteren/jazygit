package net.vanfleteren.jazygit.app.keys;

import dev.tamboui.tui.event.KeyEvent;
import net.vanfleteren.jazygit.branches.repos.RecentReposMsg;
import net.vanfleteren.jazygit.changes.cherrypick.CherryPickMsg;
import net.vanfleteren.jazygit.changes.diff.DiffOptionsMsg;
import net.vanfleteren.jazygit.changes.inprogress.InProgressMsg;
import net.vanfleteren.jazygit.chrome.help.HelpPopup;
import net.vanfleteren.jazygit.chrome.layout.LayoutMsg;
import net.vanfleteren.jazygit.chrome.layout.LayoutUpdate;
import net.vanfleteren.jazygit.chrome.settings.SettingsPanelMsg;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.github.prs.PrOverviewMsg;
import net.vanfleteren.jazygit.state.popup.PopupMsg;
import net.vanfleteren.jazygit.state.view.CommandLogFocused;
import net.vanfleteren.jazygit.ui.keys.Keymap;

/**
 * The keys that work wherever the focus is.
 */
final class GlobalKeys {

    private GlobalKeys() {}

    static Keymap register(Keymap keys) {
        // The panels show their help for ? themselves; run from the help, it closes it, as pressing it there does.
        return keys.global(new Keymap.Global.Dispatching(
                        Keymap.HELP_KEY,
                        "app.help.global.help",
                        event -> false,
                        () -> new PopupMsg.Dismissed(HelpPopup.class)))
                .global(new Keymap.Global.Dispatching(
                        "ctrl+r",
                        "app.help.global.recentRepos",
                        GlobalKeys::isRecentReposKey,
                        RecentReposMsg.Requested::new))
                .global(new Keymap.Global.Dispatching(
                        "ctrl+s",
                        "app.help.global.settings",
                        GlobalKeys::isSettingsKey,
                        SettingsPanelMsg.Requested::new))
                .global(new Keymap.Global.Dispatching(
                        "ctrl+w",
                        "app.help.global.ignoreWhitespace",
                        GlobalKeys::isWhitespaceKey,
                        DiffOptionsMsg.WhitespaceToggled::new))
                .global(new Keymap.Global.Dispatching(
                        "ctrl+d",
                        "app.help.global.diffStyle",
                        GlobalKeys::isDiffStyleKey,
                        DiffOptionsMsg.StyleToggled::new))
                // Not ctrl+i: a terminal sends it as Tab, which moves the focus before any handler sees it.
                .global(new Keymap.Global.Dispatching(
                        "ctrl+e",
                        "app.help.global.diffWords",
                        GlobalKeys::isDiffWordsKey,
                        DiffOptionsMsg.WordsToggled::new))
                .global(new Keymap.Global.Dispatching(
                        "}",
                        "app.help.global.moreContext",
                        event -> isChar(event, '}'),
                        DiffOptionsMsg.ContextWidened::new))
                .global(new Keymap.Global.Dispatching(
                        "{",
                        "app.help.global.lessContext",
                        event -> isChar(event, '{'),
                        DiffOptionsMsg.ContextNarrowed::new))
                .global(new Keymap.Global.Dispatching(
                        "ctrl+p", "app.help.global.screen", GlobalKeys::isScreenKey, PrOverviewMsg.Toggled::new))
                // Escape only gets here when nothing else, like a dialog, took it.
                .global(new Keymap.Global.Dispatching(
                        "esc", "app.help.global.cherryPickDrop", GlobalKeys::isEscape, CherryPickMsg.Dropped::new))
                .global(new Keymap.Global.Dispatching(
                        "M", "app.help.global.inProgress", GlobalKeys::isInProgressKey, InProgressMsg.Requested::new))
                .global(new Keymap.Global.Dispatching(
                        "<",
                        "app.help.global.narrower",
                        event -> isChar(event, '<'),
                        () -> new LayoutMsg.Stepped(-LayoutUpdate.STEP)))
                .global(new Keymap.Global.Dispatching(
                        ">",
                        "app.help.global.wider",
                        event -> isChar(event, '>'),
                        () -> new LayoutMsg.Stepped(LayoutUpdate.STEP)))
                // Characters, not places on the keyboard: + and - are on every layout, and on the numpad (ADR 92).
                .global(new Keymap.Global.Dispatching(
                        "+",
                        "app.help.global.taller",
                        event -> isChar(event, '+'),
                        () -> new LayoutMsg.PaneStepped(LayoutUpdate.STEP)))
                .global(new Keymap.Global.Dispatching(
                        "-",
                        "app.help.global.shorter",
                        event -> isChar(event, '-'),
                        () -> new LayoutMsg.PaneStepped(-LayoutUpdate.STEP)))
                .global(new Keymap.Global.Dispatching(
                        "Z", "app.help.global.maximize", event -> isChar(event, 'Z'), LayoutMsg.MaximizeToggled::new))
                .global(new Keymap.Global.Dispatching(
                        "@", "app.help.global.commandLog", event -> isChar(event, '@'), CommandLogFocused::new))
                .global(new Keymap.Global.Dispatching(
                        "q", "app.help.global.quit", GlobalKeys::isQuitKey, Msg.Quit::new));
    }

    /**
     * Whether {@code event} is Escape, which lets go of the commit copied to cherry-pick.
     */
    static boolean isEscape(KeyEvent event) {
        return event.isCancel();
    }

    /**
     * Whether {@code event} is the key that quits.
     */
    static boolean isQuitKey(KeyEvent event) {
        return isChar(event, 'q');
    }

    /**
     * Whether {@code event} is the character {@code c}, without Ctrl or Alt.
     */
    static boolean isChar(KeyEvent event, char c) {
        return event.isChar(c) && !event.hasCtrl() && !event.hasAlt();
    }

    /**
     * Whether {@code event} is the key that asks what to do with the merge, rebase, cherry-pick or revert the
     * repository is in the middle of.
     */
    static boolean isInProgressKey(KeyEvent event) {
        return isChar(event, 'M');
    }

    /**
     * Whether {@code event} is the shortcut that shows the settings.
     */
    static boolean isSettingsKey(KeyEvent event) {
        return event.hasCtrl() && event.isCharIgnoreCase('s');
    }

    /**
     * Whether {@code event} is the shortcut that switches between the repository and the pull requests.
     */
    static boolean isScreenKey(KeyEvent event) {
        return event.hasCtrl() && event.isCharIgnoreCase('p');
    }

    /**
     * Whether {@code event} is the shortcut that shows changes to whitespace only in the diffs, or leaves them out.
     */
    static boolean isWhitespaceKey(KeyEvent event) {
        return event.hasCtrl() && event.isCharIgnoreCase('w');
    }

    /**
     * Whether {@code event} is the shortcut that draws the diffs of the working tree, of a commit and of a stash in the
     * other style.
     */
    static boolean isDiffStyleKey(KeyEvent event) {
        return event.hasCtrl() && event.isCharIgnoreCase('d');
    }

    /**
     * Whether {@code event} is the shortcut that highlights the changed words of a line in the rich view, or stops to.
     */
    static boolean isDiffWordsKey(KeyEvent event) {
        return event.hasCtrl() && event.isCharIgnoreCase('e');
    }

    /**
     * Whether {@code event} is the shortcut that asks which recent repository to switch to.
     */
    static boolean isRecentReposKey(KeyEvent event) {
        return event.hasCtrl() && event.isCharIgnoreCase('r');
    }
}
