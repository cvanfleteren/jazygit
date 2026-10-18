package net.vanfleteren.jazygit.feature.worktree.remove;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

/**
 * The popups of removing a worktree.
 */
public final class RemoveWorktreeDialogs {

    /**
     * Asks to confirm removing a worktree.
     */
    public static final ConfirmDialog.Spec<RemoveWorktreePopup> REMOVE_WORKTREE = new ConfirmDialog.Spec<>(
            "remove-worktree",
            RemoveWorktreePopup.class,
            popup -> Messages.get("dialog.removeWorktree.title", popup.name()),
            popup -> Messages.get("dialog.removeWorktree.message", popup.location()),
            RemoveWorktreeMsg.Confirmed::new,
            RemoveWorktreeMsg.Cancelled::new);

    /**
     * Warns that a worktree has changes, and asks to remove it anyway.
     */
    public static final ConfirmDialog.Spec<ForceRemoveWorktreePopup> FORCE_REMOVE_WORKTREE = new ConfirmDialog.Spec<>(
            "force-remove-worktree",
            ForceRemoveWorktreePopup.class,
            popup -> Messages.get("dialog.removeWorktree.title", popup.name()),
            popup -> Messages.get("dialog.forceRemoveWorktree.message", popup.location()),
            RemoveWorktreeMsg.ForceConfirmed::new,
            RemoveWorktreeMsg.ForceCancelled::new);

    private RemoveWorktreeDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningTo(BranchesPanel.ID, new ConfirmDialog<>(REMOVE_WORKTREE, dispatch)),
                PopupSlot.returningTo(BranchesPanel.ID, new ConfirmDialog<>(FORCE_REMOVE_WORKTREE, dispatch)));
    }
}
