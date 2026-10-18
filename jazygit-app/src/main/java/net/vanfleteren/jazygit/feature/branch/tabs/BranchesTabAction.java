package net.vanfleteren.jazygit.feature.branch.tabs;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Switching between the local branches, the remote ones and the worktrees.
 */
public final class BranchesTabAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys -> List.of(
                    Scopes.BRANCHES, Scopes.REMOTE_BRANCHES, Scopes.DIVERGENCE)
            .foldLeft(
                    keys,
                    (added, scope) -> added.add(
                            scope,
                            Binding.always("[", "help.branches.previousTab", () -> new BranchesTabMsg.Switched(-1)),
                            Binding.always("]", "help.branches.nextTab", () -> new BranchesTabMsg.Switched(1)))));

    private BranchesTabAction() {}
}
