package net.vanfleteren.jazygit.changes.commit.revert;

import net.vanfleteren.jazygit.ui.keys.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

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
