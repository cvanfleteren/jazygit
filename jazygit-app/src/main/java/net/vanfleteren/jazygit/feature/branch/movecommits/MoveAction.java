package net.vanfleteren.jazygit.feature.branch.movecommits;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Moving the selected commit, and the ones after it, to a new or an existing branch.
 */
public final class MoveAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                    Scopes.COMMITS,
                    Binding.onSelected(
                            "N", "help.commits.moveToBranch", commit -> new MoveMsg.Requested(commit.sha()))),
            MoveDialogs::popups);

    private MoveAction() {}
}
