package net.vanfleteren.jazygit.features.changes.commit.reset;

import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Resetting the checked out branch to the selected commit, also to that of an entry of the reflog (ADR 194).
 */
public final class ResetAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                            Scopes.COMMITS,
                            Binding.onSelected(
                                    "g", "changes.help.commits.reset", commit -> new ResetMsg.Requested(commit.sha())))
                    .add(
                            Scopes.REFLOG,
                            Binding.onSelected(
                                    "g", "changes.help.commits.reset", entry -> new ResetMsg.Requested(entry.sha()))),
            ResetDialogs::popups);

    private ResetAction() {}
}
