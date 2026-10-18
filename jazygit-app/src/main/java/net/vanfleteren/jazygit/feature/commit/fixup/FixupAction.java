package net.vanfleteren.jazygit.feature.commit.fixup;

import net.vanfleteren.jazygit.feature.commit.CommitAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Fixing up the selected commit into the one below it.
 */
public final class FixupAction {

    public static final CommitAction ACTION = new CommitAction(
            keys -> keys.add(
                    Scopes.COMMITS,
                    Binding.onSelected("f", "help.commits.fixup", commit -> new FixupMsg.Requested(commit.sha()))),
            FixupDialogs::popups);

    private FixupAction() {}
}
