package net.vanfleteren.jazygit.branches.forcedelete;

import net.vanfleteren.jazygit.ui.Feature;
import net.vanfleteren.jazygit.ui.FeatureAction;

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
