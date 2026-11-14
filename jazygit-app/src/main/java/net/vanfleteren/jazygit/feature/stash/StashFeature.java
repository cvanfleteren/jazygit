package net.vanfleteren.jazygit.feature.stash;

import net.vanfleteren.jazygit.feature.Feature;
import net.vanfleteren.jazygit.feature.FeatureAction;

/**
 * Stashing the changes, and popping and dropping a stash.
 */
public final class StashFeature {

    /**
     * What the app knows of it: its keys and its popups.
     */
    public static final Feature FEATURE = Feature.of(new FeatureAction(StashKeys::register, StashDialogs::popups));

    private StashFeature() {}
}
