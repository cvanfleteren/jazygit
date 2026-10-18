package net.vanfleteren.jazygit.feature.commit.reword;

import net.vanfleteren.jazygit.feature.commit.CommitAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Rewording the selected commit.
 */
public final class RewordAction {

    public static final CommitAction ACTION = new CommitAction(
            keys -> keys.add(
                    Scopes.COMMITS,
                    Binding.onSelected("r", "help.commits.reword", commit -> new RewordMsg.Requested(commit.sha()))),
            RewordDialogs::popups);

    private RewordAction() {}
}
