package net.vanfleteren.jazygit.feature.commit.create;

import net.vanfleteren.jazygit.feature.commit.CommitAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Committing what is staged, from the files.
 */
public final class CreateCommitAction {

    public static final CommitAction ACTION = new CommitAction(
            keys -> keys.add(Scopes.FILES, Binding.always("c", "help.files.commit", CommitMsg.Requested::new)),
            CreateCommitDialogs::popups);

    private CreateCommitAction() {}
}
