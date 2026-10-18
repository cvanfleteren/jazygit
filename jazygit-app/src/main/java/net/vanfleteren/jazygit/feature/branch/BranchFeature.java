package net.vanfleteren.jazygit.feature.branch;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutAction;
import net.vanfleteren.jazygit.feature.branch.delete.DeleteBranchAction;
import net.vanfleteren.jazygit.feature.branch.merge.MergeAction;
import net.vanfleteren.jazygit.feature.branch.movecommits.MoveAction;
import net.vanfleteren.jazygit.feature.branch.newbranch.NewBranchAction;
import net.vanfleteren.jazygit.feature.branch.pull.PullAction;
import net.vanfleteren.jazygit.feature.branch.push.PushAction;
import net.vanfleteren.jazygit.feature.branch.rebase.RebaseAction;
import net.vanfleteren.jazygit.feature.branch.rename.RenameBranchAction;
import net.vanfleteren.jazygit.feature.branch.tabs.BranchesTabAction;
import net.vanfleteren.jazygit.feature.branch.upstream.UpstreamAction;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.PopupSlot;

/**
 * The branch operations: checking out, pushing, pulling, creating, moving commits to a new branch, merging, rebasing, renaming, deleting, the
 * upstream options, and switching between the lists of the branches pane. Adding an operation is adding its
 * subpackage, and listing its action here.
 */
public final class BranchFeature {

    /**
     * The operations, in the order their keys are listed in a panel.
     */
    private static final List<FeatureAction> ACTIONS = List.of(
            CheckoutAction.ACTION,
            PushAction.ACTION,
            PullAction.ACTION,
            NewBranchAction.ACTION,
            MoveAction.ACTION,
            MergeAction.ACTION,
            RebaseAction.ACTION,
            RenameBranchAction.ACTION,
            DeleteBranchAction.ACTION,
            UpstreamAction.ACTION,
            BranchesTabAction.ACTION);

    private BranchFeature() {}

    public static Keymap register(Keymap keys) {
        return FeatureAction.register(ACTIONS, keys);
    }

    /**
     * The popups of the branch operations, which send what the user does to {@code dispatch}.
     */
    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return FeatureAction.popups(ACTIONS, dispatch);
    }
}
