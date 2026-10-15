package net.vanfleteren.jazygit.feature.worktree;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;
import net.vanfleteren.jazygit.ui.widgets.TextInputDialog;

/**
 * The popups of the worktree operations.
 */
public final class WorktreeDialogs {

    /**
     * Asks where the new worktree for a branch goes, starting out next to the main worktree. A blank location
     * keeps it open.
     */
    public static final TextInputDialog.Spec<NewWorktreePopup> NEW_WORKTREE = new TextInputDialog.Spec<>(
            "new-worktree", NewWorktreePopup.class,
            popup -> Messages.get("dialog.newWorktree.title", popup.branch().value()),
            Option.of(Messages.get("dialog.newWorktree.placeholder")),
            NewWorktreePopup::location, WorktreeMsg.NewConfirmed::new, WorktreeMsg.NewCancelled::new);

    /**
     * Asks to confirm removing a worktree.
     */
    public static final ConfirmDialog.Spec<RemoveWorktreePopup> REMOVE_WORKTREE = new ConfirmDialog.Spec<>(
            "remove-worktree", RemoveWorktreePopup.class,
            popup -> Messages.get("dialog.removeWorktree.title", popup.name()),
            popup -> Messages.get("dialog.removeWorktree.message", popup.location()),
            WorktreeMsg.RemoveConfirmed::new, WorktreeMsg.RemoveCancelled::new);

    /**
     * Warns that a worktree has changes, and asks to remove it anyway.
     */
    public static final ConfirmDialog.Spec<ForceRemoveWorktreePopup> FORCE_REMOVE_WORKTREE = new ConfirmDialog.Spec<>(
            "force-remove-worktree", ForceRemoveWorktreePopup.class,
            popup -> Messages.get("dialog.removeWorktree.title", popup.name()),
            popup -> Messages.get("dialog.forceRemoveWorktree.message", popup.location()),
            WorktreeMsg.ForceConfirmed::new, WorktreeMsg.ForceCancelled::new);

    private WorktreeDialogs() {
    }
}
