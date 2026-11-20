package net.vanfleteren.jazygit.features.chrome.help;

import net.vanfleteren.jazygit.kernel.state.popup.PopupMsg;
import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;

/**
 * The help, which lists the keys of the panel that has the focus.
 */
public final class HelpFeature {

    /**
     * The key that shows the help. The panels show their help for ? themselves; run from the help, it closes it, as
     * pressing it there does.
     */
    private static final FeatureAction GLOBAL_KEY =
            FeatureAction.ofKeys(keys -> keys.global(Keymap.Global.Dispatching.listedOnly(
                    Keymap.HELP_KEY, "chrome.help.global.help", () -> new PopupMsg.Dismissed(HelpPopup.class))));

    /**
     * What the app knows of it: its keys and its popups.
     */
    public static final Feature FEATURE = Feature.of(GLOBAL_KEY, FeatureAction.ofPopups(HelpDialog::popups));

    private HelpFeature() {}
}
