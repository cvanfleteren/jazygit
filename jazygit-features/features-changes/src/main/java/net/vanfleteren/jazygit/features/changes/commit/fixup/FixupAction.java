package net.vanfleteren.jazygit.features.changes.commit.fixup;

import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Fixing up the selected commit into the one below it.
 */
public final class FixupAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                    Scopes.COMMITS,
                    Binding.onSelected(
                            "f", "changes.help.commits.fixup", commit -> new FixupMsg.Requested(commit.sha()))),
            FixupDialogs::popups);

    private FixupAction() {}
}
