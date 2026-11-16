package net.vanfleteren.jazygit.chrome.settings;

import net.vanfleteren.jazygit.ui.Feature;
import net.vanfleteren.jazygit.ui.FeatureAction;

/**
 * The settings panel.
 */
public final class SettingsFeature {

    /**
     * What the app knows of it: its keys and its popups.
     */
    public static final Feature FEATURE = Feature.of(FeatureAction.ofPopups(SettingsPanelDialogs::popups));

    private SettingsFeature() {}
}
