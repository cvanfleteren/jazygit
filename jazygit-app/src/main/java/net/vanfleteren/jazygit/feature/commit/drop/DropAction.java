package net.vanfleteren.jazygit.feature.commit.drop;

import net.vanfleteren.jazygit.feature.commit.CommitAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Dropping the selected commit.
 */
public final class DropAction {

    public static final CommitAction ACTION = new CommitAction(
            keys -> keys.add(
                    Scopes.COMMITS,
                    Binding.onSelected("d", "help.commits.drop", commit -> new DropMsg.Requested(commit.sha()))),
            DropDialogs::popups);

    private DropAction() {}
}
