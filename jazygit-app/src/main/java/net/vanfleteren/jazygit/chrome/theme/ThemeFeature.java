package net.vanfleteren.jazygit.chrome.theme;

import net.vanfleteren.jazygit.ui.keys.Feature;
import net.vanfleteren.jazygit.ui.keys.FeatureAction;

/**
 * Picking the theme.
 */
public final class ThemeFeature {

    /**
     * What the app knows of it: its keys and its popups.
     */
    public static final Feature FEATURE = Feature.of(FeatureAction.ofPopups(ThemeDialog::popups));

    private ThemeFeature() {}
}
