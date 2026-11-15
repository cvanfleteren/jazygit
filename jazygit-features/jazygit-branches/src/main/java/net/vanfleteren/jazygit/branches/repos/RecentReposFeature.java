package net.vanfleteren.jazygit.branches.repos;

import net.vanfleteren.jazygit.ui.Feature;
import net.vanfleteren.jazygit.ui.FeatureAction;

/**
 * Switching to a recent repository.
 */
public final class RecentReposFeature {

    /**
     * What the app knows of it: its keys and its popups.
     */
    public static final Feature FEATURE = Feature.of(FeatureAction.ofPopups(RecentReposDialog::popups));

    private RecentReposFeature() {}
}
