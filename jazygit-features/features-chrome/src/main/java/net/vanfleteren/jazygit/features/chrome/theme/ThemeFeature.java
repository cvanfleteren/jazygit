package net.vanfleteren.jazygit.features.chrome.theme;

import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;

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
