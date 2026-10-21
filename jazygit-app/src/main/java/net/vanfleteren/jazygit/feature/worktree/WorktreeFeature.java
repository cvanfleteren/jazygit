package net.vanfleteren.jazygit.feature.worktree;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeAction;
import net.vanfleteren.jazygit.feature.worktree.lock.LockWorktreeAction;
import net.vanfleteren.jazygit.feature.worktree.prune.PruneWorktreesAction;
import net.vanfleteren.jazygit.feature.worktree.remove.RemoveWorktreeAction;
import net.vanfleteren.jazygit.feature.worktree.switchto.SwitchWorktreeAction;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.PopupSlot;

/**
 * The worktree operations: adding one for a branch, switching to one, removing one, pruning the ones whose
 * directory is gone, and locking one. Adding an operation is adding its subpackage, and listing its action here.
 */
public final class WorktreeFeature {

    /**
     * The operations, in the order their keys are listed in a panel. Switching from the worktrees to the other lists
     * of the branches pane is the branch feature's, as deleting a branch removes a worktree.
     */
    private static final List<FeatureAction> ACTIONS = List.of(
            AddWorktreeAction.ACTION,
            SwitchWorktreeAction.ACTION,
            RemoveWorktreeAction.ACTION,
            PruneWorktreesAction.ACTION,
            LockWorktreeAction.ACTION);

    private WorktreeFeature() {}

    public static Keymap register(Keymap keys) {
        return FeatureAction.register(ACTIONS, keys);
    }

    /**
     * The popups of the worktree operations, which send what the user does to {@code dispatch}.
     */
    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return FeatureAction.popups(ACTIONS, dispatch);
    }
}
