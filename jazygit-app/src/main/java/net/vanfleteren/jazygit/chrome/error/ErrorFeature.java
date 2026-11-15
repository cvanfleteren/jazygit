package net.vanfleteren.jazygit.chrome.error;

import net.vanfleteren.jazygit.ui.keys.Feature;
import net.vanfleteren.jazygit.ui.keys.FeatureAction;

/**
 * Showing what went wrong.
 */
public final class ErrorFeature {

    /**
     * What the app knows of it: its keys and its popups.
     */
    public static final Feature FEATURE = Feature.of(FeatureAction.ofPopups(ErrorDialog::popups));

    private ErrorFeature() {}
}
