package net.vanfleteren.jazygit.feature.commit.reset;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Resetting the checked out branch to the selected commit.
 */
public final class ResetAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                    Scopes.COMMITS,
                    Binding.onSelected("g", "help.commits.reset", commit -> new ResetMsg.Requested(commit.sha()))),
            ResetDialogs::popups);

    private ResetAction() {}
}
