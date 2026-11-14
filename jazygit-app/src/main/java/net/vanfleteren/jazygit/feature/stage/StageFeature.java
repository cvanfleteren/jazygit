package net.vanfleteren.jazygit.feature.stage;

import net.vanfleteren.jazygit.feature.Feature;
import net.vanfleteren.jazygit.feature.FeatureAction;

/**
 * Staging and unstaging files.
 */
public final class StageFeature {

    /**
     * What the app knows of it: its keys and its popups.
     */
    public static final Feature FEATURE = Feature.of(FeatureAction.ofKeys(StageKeys::register));

    private StageFeature() {}
}
