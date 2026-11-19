package net.vanfleteren.jazygit.features.changes.commit.reword;

import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Rewording the selected commit.
 */
public final class RewordAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                    Scopes.COMMITS,
                    Binding.onSelected(
                            "r", "changes.help.commits.reword", commit -> new RewordMsg.Requested(commit.sha()))),
            RewordDialogs::popups);

    private RewordAction() {}
}
