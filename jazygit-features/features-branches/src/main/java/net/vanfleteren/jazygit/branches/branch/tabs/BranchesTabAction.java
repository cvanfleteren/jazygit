package net.vanfleteren.jazygit.branches.branch.tabs;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.ui.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Switching between the local branches, the remote ones and the worktrees.
 */
public final class BranchesTabAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys -> List.of(
                    Scopes.BRANCHES, Scopes.REMOTE_BRANCHES, Scopes.DIVERGENCE, Scopes.BRANCH_COMMITS, Scopes.WORKTREES)
            .foldLeft(
                    keys,
                    (added, scope) -> added.add(
                            scope,
                            Binding.always(
                                    "[", "branches.help.branches.previousTab", () -> new BranchesTabMsg.Switched(-1)),
                            Binding.always(
                                    "]", "branches.help.branches.nextTab", () -> new BranchesTabMsg.Switched(1)))));

    private BranchesTabAction() {}
}
