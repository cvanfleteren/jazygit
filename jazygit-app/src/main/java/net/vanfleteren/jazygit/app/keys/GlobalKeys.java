package net.vanfleteren.jazygit.app.keys;

import dev.tamboui.tui.bindings.KeyTrigger;
import dev.tamboui.tui.event.KeyEvent;
import net.vanfleteren.jazygit.features.chrome.layout.LayoutMsg;
import net.vanfleteren.jazygit.features.chrome.layout.LayoutUpdate;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.view.CommandLogFocused;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;

/**
 * The keys of the app that work wherever the focus is: the layout, the command log and quitting. The features bring
 * their own, in their {@link net.vanfleteren.jazygit.kernel.ui.Feature}s.
 */
final class GlobalKeys {

    private GlobalKeys() {}

    static Keymap register(Keymap keys) {
        return keys.global(new Keymap.Global.Dispatching(
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
     * Whether {@code event} is the key that quits.
     */
    static boolean isQuitKey(KeyEvent event) {
        return isChar(event, 'q');
    }

    /**
     * Whether {@code event} is the character {@code c}, without Ctrl, Alt, Super, Hyper or Meta.
     */
    static boolean isChar(KeyEvent event, char c) {
        return KeyTrigger.ch(c).matches(event);
    }
}
