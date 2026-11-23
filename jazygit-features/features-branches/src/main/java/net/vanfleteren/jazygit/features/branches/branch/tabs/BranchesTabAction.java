package net.vanfleteren.jazygit.features.branches.branch.tabs;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Switching between the tabs of the branches pane (ADR 185): the local branches, the remote ones, the worktrees, and
 * those the features add. The keys are those of the tabs of any pane, {@link Scopes#PANE_TABS}, which every list of
 * the pane hosts: also the divergence and the commits of a branch, which switching closes.
 */
public final class BranchesTabAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys -> List.of(
                    Scopes.BRANCHES, Scopes.REMOTE_BRANCHES, Scopes.DIVERGENCE, Scopes.BRANCH_COMMITS, Scopes.WORKTREES)
            .foldLeft(keys, (added, scope) -> added.host(scope, Scopes.PANE_TABS)));

    private BranchesTabAction() {}
}
