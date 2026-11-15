package net.vanfleteren.jazygit.changes.ahead;

import net.vanfleteren.jazygit.state.load.AheadMsg;
import net.vanfleteren.jazygit.ui.keys.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Listing only the commits of a branch that are not on its base, in the commits pane and in the log of the
 * highlighted branch alike: one setting, with a key in each pane.
 */
public final class AheadAction {

    /**
     * The key in the commits pane.
     */
    public static final FeatureAction COMMITS = FeatureAction.ofKeys(
            keys -> keys.add(Scopes.COMMITS, Binding.always("b", "help.commits.onlyAhead", AheadMsg.Toggled::new)));

    /**
     * The key in the tabs of the branches pane that show the log of a branch, and in the commits of a branch listed
     * in their place, listed with the branch operations.
     */
    public static final FeatureAction BRANCHES = FeatureAction.ofKeys(
            keys -> keys.add(Scopes.BRANCHES, Binding.always("b", "help.branches.onlyAhead", AheadMsg.Toggled::new))
                    .add(Scopes.REMOTE_BRANCHES, Binding.always("b", "help.branches.onlyAhead", AheadMsg.Toggled::new))
                    .add(Scopes.BRANCH_COMMITS, Binding.always("b", "help.branches.onlyAhead", AheadMsg.Toggled::new))
                    .add(Scopes.BUILDS, Binding.always("b", "help.builds.onlyAhead", AheadMsg.Toggled::new)));

    private AheadAction() {}
}
