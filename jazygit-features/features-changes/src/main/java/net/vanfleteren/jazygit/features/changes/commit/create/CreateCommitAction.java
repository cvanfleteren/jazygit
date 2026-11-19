package net.vanfleteren.jazygit.features.changes.commit.create;

import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Committing what is staged, from the files.
 */
public final class CreateCommitAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(Scopes.FILES, Binding.always("c", "changes.help.files.commit", CommitMsg.Requested::new)),
            CreateCommitDialogs::popups);

    private CreateCommitAction() {}
}
