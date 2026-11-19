package net.vanfleteren.jazygit.features.changes.stage;

import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;

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
