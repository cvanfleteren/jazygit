package net.vanfleteren.jazygit.changes.commit.drop;

import net.vanfleteren.jazygit.ui.keys.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Dropping the selected commit.
 */
public final class DropAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                    Scopes.COMMITS,
                    Binding.onSelected("d", "help.commits.drop", commit -> new DropMsg.Requested(commit.sha()))),
            DropDialogs::popups);

    private DropAction() {}
}
