package net.vanfleteren.jazygit.feature.inprogress;

import net.vanfleteren.jazygit.feature.Feature;
import net.vanfleteren.jazygit.feature.FeatureAction;

/**
 * Continuing or aborting the merge, rebase, cherry-pick or revert the repository is in the middle of.
 */
public final class InProgressFeature {

    /**
     * What the app knows of it: its keys and its popups.
     */
    public static final Feature FEATURE = Feature.of(FeatureAction.ofPopups(InProgressDialogs::popups));

    private InProgressFeature() {}
}
