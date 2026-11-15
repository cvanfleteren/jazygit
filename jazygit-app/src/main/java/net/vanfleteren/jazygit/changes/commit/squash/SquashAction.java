package net.vanfleteren.jazygit.changes.commit.squash;

import net.vanfleteren.jazygit.ui.keys.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Squashing the selected commit into the one below it.
 */
public final class SquashAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                    Scopes.COMMITS,
                    Binding.onSelected("s", "help.commits.squash", commit -> new SquashMsg.Requested(commit.sha()))),
            SquashDialogs::popups);

    private SquashAction() {}
}
