package net.vanfleteren.jazygit.feature.commit.squash;

import net.vanfleteren.jazygit.feature.commit.CommitAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Squashing the selected commit into the one below it.
 */
public final class SquashAction {

    public static final CommitAction ACTION = new CommitAction(
            keys -> keys.add(
                    Scopes.COMMITS,
                    Binding.onSelected("s", "help.commits.squash", commit -> new SquashMsg.Requested(commit.sha()))),
            SquashDialogs::popups);

    private SquashAction() {}
}
