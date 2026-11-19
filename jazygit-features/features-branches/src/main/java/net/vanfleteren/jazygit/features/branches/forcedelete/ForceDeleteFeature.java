package net.vanfleteren.jazygit.features.branches.forcedelete;

import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;

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
