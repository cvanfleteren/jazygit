package net.vanfleteren.jazygit.feature.forcedelete;

import net.vanfleteren.jazygit.feature.Feature;
import net.vanfleteren.jazygit.feature.FeatureAction;

/**
 * Deleting a branch that is not merged, once git refused to.
 */
public final class ForceDeleteFeature {

    /**
     * What the app knows of it: its keys and its popups.
     */
    public static final Feature FEATURE = Feature.of(FeatureAction.ofPopups(ForceDeleteDialog::popups));

    private ForceDeleteFeature() {}
}
