package net.vanfleteren.jazygit.changes.commit.reset;

import net.vanfleteren.jazygit.ui.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

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
