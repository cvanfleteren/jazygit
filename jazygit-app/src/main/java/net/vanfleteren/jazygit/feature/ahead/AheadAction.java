package net.vanfleteren.jazygit.feature.ahead;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.AheadMsg;

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
     * The key in the tabs of the branches pane that show the log of a branch, listed with the branch operations.
     */
    public static final FeatureAction BRANCHES = FeatureAction.ofKeys(keys -> keys.add(
                    Scopes.BRANCHES, Binding.always("b", "help.branches.onlyAhead", AheadMsg.Toggled::new))
            .add(Scopes.REMOTE_BRANCHES, Binding.always("b", "help.branches.onlyAhead", AheadMsg.Toggled::new)));

    private AheadAction() {}
}
