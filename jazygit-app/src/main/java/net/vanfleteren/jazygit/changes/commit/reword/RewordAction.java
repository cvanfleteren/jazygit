package net.vanfleteren.jazygit.changes.commit.reword;

import net.vanfleteren.jazygit.ui.keys.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Rewording the selected commit.
 */
public final class RewordAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                    Scopes.COMMITS,
                    Binding.onSelected("r", "help.commits.reword", commit -> new RewordMsg.Requested(commit.sha()))),
            RewordDialogs::popups);

    private RewordAction() {}
}
