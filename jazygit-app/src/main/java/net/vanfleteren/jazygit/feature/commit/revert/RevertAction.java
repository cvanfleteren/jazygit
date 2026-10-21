package net.vanfleteren.jazygit.feature.commit.revert;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Reverting the selected commit.
 */
public final class RevertAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                    Scopes.COMMITS,
                    Binding.onSelected("t", "help.commits.revert", commit -> new RevertMsg.Requested(commit.sha()))),
            RevertDialogs::popups);

    private RevertAction() {}
}
