package net.vanfleteren.jazygit.feature.worktree.remove;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

/**
 * The popups of removing a worktree.
 */
public final class RemoveWorktreeDialogs {

    /**
     * Asks whether to remove a worktree, keeping its branch or deleting it too. Deleting it is only offered when the
     * worktree has a branch checked out, and only applies when no operation runs on it.
     */
    public static final ChoiceDialog.Spec<RemoveWorktreePopup> REMOVE_WORKTREE = new ChoiceDialog.Spec<>(
            "remove-worktree",
            RemoveWorktreePopup.class,
            popup -> Messages.get("dialog.removeWorktree.title", popup.name()),
            List.of(
                    new Choice<>('r', "dialog.removeWorktree.keepBranch", () -> new RemoveWorktreeMsg.Chosen(false)),
                    new Choice<RemoveWorktreePopup>(
                                    'b',
                                    popup -> Messages.get(
                                            "dialog.removeWorktree.deleteBranch",
                                            popup.branch()
                                                    .map(BranchName::value)
                                                    .getOrElse("")),
                                    () -> new RemoveWorktreeMsg.Chosen(true),
                                    (model, popup) -> RemoveWorktreeUpdate.branchDeletable(model, popup))
                            .shownWhen((model, popup) -> popup.branch().isDefined())));

    /**
     * Warns that a worktree has changes, and asks to remove it anyway.
     */
    public static final ConfirmDialog.Spec<ForceRemoveWorktreePopup> FORCE_REMOVE_WORKTREE =
            ConfirmDialog.Spec.confirming(
                    "force-remove-worktree",
                    ForceRemoveWorktreePopup.class,
                    popup -> Messages.get("dialog.removeWorktree.title", popup.name()),
                    popup -> Messages.get(
                            "dialog.forceRemoveWorktree.message",
                            popup.removal().location()));

    private RemoveWorktreeDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningTo(BranchesPanel.ID, new ChoiceDialog<>(REMOVE_WORKTREE, dispatch)),
                PopupSlot.returningTo(BranchesPanel.ID, new ConfirmDialog<>(FORCE_REMOVE_WORKTREE, dispatch)));
    }
}
