package net.vanfleteren.jazygit.features.chrome.settings;

import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;

/**
 * The settings panel.
 */
public final class SettingsFeature {

    /**
     * The shortcut that shows the settings, wherever the focus is.
     */
    private static final FeatureAction GLOBAL_KEY = FeatureAction.ofKeys(keys -> keys.global(
            Keymap.Global.Dispatching.ofCtrl('s', "app.help.global.settings", SettingsPanelMsg.Requested::new)));

    /**
     * What the app knows of it: its keys and its popups.
     */
    public static final Feature FEATURE = Feature.of(GLOBAL_KEY, FeatureAction.ofPopups(SettingsPanelDialogs::popups));

    private SettingsFeature() {}
}
