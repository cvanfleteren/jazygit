package net.vanfleteren.jazygit.features.changes.commit.revert;

import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Reverting the selected commit.
 */
public final class RevertAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                    Scopes.COMMITS,
                    Binding.onSelected(
                            "t", "changes.help.commits.revert", commit -> new RevertMsg.Requested(commit.sha()))),
            RevertDialogs::popups);

    private RevertAction() {}
}
