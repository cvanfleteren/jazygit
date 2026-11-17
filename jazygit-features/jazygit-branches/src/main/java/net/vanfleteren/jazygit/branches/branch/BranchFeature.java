package net.vanfleteren.jazygit.branches.branch;

import net.vanfleteren.jazygit.branches.branch.base.BaseAction;
import net.vanfleteren.jazygit.branches.branch.checkout.CheckoutAction;
import net.vanfleteren.jazygit.branches.branch.commits.BranchCommitsAction;
import net.vanfleteren.jazygit.branches.branch.delete.DeleteBranchAction;
import net.vanfleteren.jazygit.branches.branch.merge.MergeAction;
import net.vanfleteren.jazygit.branches.branch.movecommits.MoveAction;
import net.vanfleteren.jazygit.branches.branch.newbranch.NewBranchAction;
import net.vanfleteren.jazygit.branches.branch.nuke.NukeBranchAction;
import net.vanfleteren.jazygit.branches.branch.pull.PullAction;
import net.vanfleteren.jazygit.branches.branch.push.PushAction;
import net.vanfleteren.jazygit.branches.branch.rebase.RebaseAction;
import net.vanfleteren.jazygit.branches.branch.rename.RenameBranchAction;
import net.vanfleteren.jazygit.branches.branch.stack.StackAction;
import net.vanfleteren.jazygit.branches.branch.tabs.BranchesTabAction;
import net.vanfleteren.jazygit.branches.branch.upstream.UpstreamAction;
import net.vanfleteren.jazygit.ui.Feature;

/**
 * The branch operations: checking out, pushing, pulling, creating, moving commits to a new branch, merging, rebasing, renaming, deleting, nuking, the
 * upstream options, viewing the commits of a branch, and switching between the lists of the branches pane. Adding an operation is adding its
 * subpackage, and listing its action here.
 */
public final class BranchFeature {

    /**
     * The operations, in the order their keys are listed in a panel.
     */
    public static final Feature FEATURE = Feature.of(
            CheckoutAction.ACTION,
            PushAction.ACTION,
            PullAction.ACTION,
            NewBranchAction.ACTION,
            MoveAction.ACTION,
            MergeAction.ACTION,
            RebaseAction.ACTION,
            RenameBranchAction.ACTION,
            DeleteBranchAction.ACTION,
            NukeBranchAction.ACTION,
            BranchCommitsAction.ACTION,
            StackAction.ACTION,
            UpstreamAction.ACTION,
            BaseAction.ACTION,
            BranchesTabAction.ACTION);

    private BranchFeature() {}
}
