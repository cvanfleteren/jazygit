package net.vanfleteren.jazygit.feature.error;

import net.vanfleteren.jazygit.feature.Feature;
import net.vanfleteren.jazygit.feature.FeatureAction;

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
