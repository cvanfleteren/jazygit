package net.vanfleteren.jazygit.features.changes.inprogress;

import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;

/**
 * Continuing or aborting the merge, rebase, cherry-pick or revert the repository is in the middle of.
 */
public final class InProgressFeature {

    /**
     * The key that asks what to do with the merge, rebase, cherry-pick or revert the repository is in the middle of,
     * wherever the focus is.
     */
    private static final FeatureAction GLOBAL_KEY = FeatureAction.ofKeys(keys -> keys.global(
            Keymap.Global.Dispatching.ofChar('M', "changes.help.global.inProgress", InProgressMsg.Requested::new)));

    /**
     * What the app knows of it: its keys and its popups.
     */
    public static final Feature FEATURE = Feature.of(GLOBAL_KEY, FeatureAction.ofPopups(InProgressDialogs::popups));

    private InProgressFeature() {}
}
