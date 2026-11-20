package net.vanfleteren.jazygit.features.branches.repos;

import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;

/**
 * Switching to a recent repository.
 */
public final class RecentReposFeature {

    /**
     * The shortcut that asks which recent repository to switch to, wherever the focus is.
     */
    private static final FeatureAction GLOBAL_KEY = FeatureAction.ofKeys(keys -> keys.global(
            Keymap.Global.Dispatching.ofCtrl('r', "branches.help.global.recentRepos", RecentReposMsg.Requested::new)));

    /**
     * What the app knows of it: its keys and its popups.
     */
    public static final Feature FEATURE = Feature.of(GLOBAL_KEY, FeatureAction.ofPopups(RecentReposDialog::popups));

    private RecentReposFeature() {}
}
