package net.vanfleteren.jazygit.feature.worktree;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.feature.branch.tabs.BranchesTabMsg;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeAction;
import net.vanfleteren.jazygit.feature.worktree.remove.RemoveWorktreeAction;
import net.vanfleteren.jazygit.feature.worktree.switchto.SwitchWorktreeAction;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.PopupSlot;

/**
 * The worktree operations: adding one for a branch, switching to one, and removing one. Adding an operation is
 * adding its subpackage, and listing its action here.
 */
public final class WorktreeFeature {

    /**
     * Switching from the worktrees to the other lists of the branches pane, as the branches do.
     */
    private static final FeatureAction TABS = FeatureAction.ofKeys(keys -> keys.add(
            Scopes.WORKTREES,
            Binding.always("[", "help.branches.previousTab", () -> new BranchesTabMsg.Switched(-1)),
            Binding.always("]", "help.branches.nextTab", () -> new BranchesTabMsg.Switched(1))));

    /**
     * The operations, in the order their keys are listed in a panel.
     */
    private static final List<FeatureAction> ACTIONS =
            List.of(AddWorktreeAction.ACTION, SwitchWorktreeAction.ACTION, RemoveWorktreeAction.ACTION, TABS);

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
