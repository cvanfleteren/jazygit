package net.vanfleteren.jazygit.features.changes.commit.drop;

import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Dropping the selected commit.
 */
public final class DropAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                    Scopes.COMMITS,
                    Binding.onSelected(
                            "d", "changes.help.commits.drop", commit -> new DropMsg.Requested(commit.sha()))),
            DropDialogs::popups);

    private DropAction() {}
}
