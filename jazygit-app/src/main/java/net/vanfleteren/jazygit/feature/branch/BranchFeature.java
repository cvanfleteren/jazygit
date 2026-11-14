package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.feature.Feature;
import net.vanfleteren.jazygit.feature.ahead.AheadAction;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutAction;
import net.vanfleteren.jazygit.feature.branch.commits.BranchCommitsAction;
import net.vanfleteren.jazygit.feature.branch.delete.DeleteBranchAction;
import net.vanfleteren.jazygit.feature.branch.merge.MergeAction;
import net.vanfleteren.jazygit.feature.branch.movecommits.MoveAction;
import net.vanfleteren.jazygit.feature.branch.newbranch.NewBranchAction;
import net.vanfleteren.jazygit.feature.branch.nuke.NukeBranchAction;
import net.vanfleteren.jazygit.feature.branch.pull.PullAction;
import net.vanfleteren.jazygit.feature.branch.push.PushAction;
import net.vanfleteren.jazygit.feature.branch.rebase.RebaseAction;
import net.vanfleteren.jazygit.feature.branch.rename.RenameBranchAction;
import net.vanfleteren.jazygit.feature.branch.stack.StackAction;
import net.vanfleteren.jazygit.feature.branch.tabs.BranchesTabAction;
import net.vanfleteren.jazygit.feature.branch.upstream.UpstreamAction;

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
            AheadAction.BRANCHES,
            BranchesTabAction.ACTION);

    private BranchFeature() {}
}
