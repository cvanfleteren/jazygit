package net.vanfleteren.jazygit.feature.branch.delete;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.git.model.DeleteScope;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

/**
 * The popups of deleting a branch, local or remote.
 */
public final class DeleteBranchDialogs {

    /**
     * Asks where to delete a branch. The options that act on the remote only apply when the branch has a
     * remote branch that can be deleted, the ones that act locally when no other worktree has it checked out.
     */
    public static final ChoiceDialog.Spec<DeleteBranchPopup> DELETE_BRANCH = new ChoiceDialog.Spec<>(
            "delete-branch",
            DeleteBranchPopup.class,
            popup -> Messages.get("dialog.deleteBranch.title", popup.branch().value()),
            List.of(
                    new Choice<>(
                            'd',
                            "dialog.deleteBranch.local",
                            () -> new DeleteBranchMsg.Chosen(DeleteScope.LOCAL),
                            DeleteBranchDialogs::localDeletable),
                    new Choice<>(
                            'r',
                            "dialog.deleteBranch.remote",
                            () -> new DeleteBranchMsg.Chosen(DeleteScope.REMOTE),
                            DeleteBranchDialogs::remoteDeletable),
                    new Choice<>(
                            'b',
                            "dialog.deleteBranch.both",
                            () -> new DeleteBranchMsg.Chosen(DeleteScope.BOTH),
                            (model, popup) -> localDeletable(model, popup) && remoteDeletable(model, popup))),
            DeleteBranchMsg.Cancelled::new);

    /**
     * Asks to confirm deleting a remote branch on its remote.
     */
    public static final ConfirmDialog.Spec<DeleteRemoteBranchPopup> DELETE_REMOTE_BRANCH = new ConfirmDialog.Spec<>(
            "delete-remote-branch",
            DeleteRemoteBranchPopup.class,
            popup -> Messages.get(
                    "dialog.deleteRemoteBranch.title", popup.branch().shortName()),
            popup -> Messages.get(
                    "dialog.deleteRemoteBranch.message", popup.branch().shortName()),
            DeleteBranchMsg.RemoteConfirmed::new,
            DeleteBranchMsg.RemoteCancelled::new);

    private DeleteBranchDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningTo(BranchesPanel.ID, new ChoiceDialog<>(DELETE_BRANCH, dispatch)),
                PopupSlot.returningTo(BranchesPanel.ID, new ConfirmDialog<>(DELETE_REMOTE_BRANCH, dispatch)));
    }

    private static boolean remoteDeletable(Model model, DeleteBranchPopup popup) {
        return DeleteBranchUpdate.remoteDeletable(model, popup.branch());
    }

    private static boolean localDeletable(Model model, DeleteBranchPopup popup) {
        return DeleteBranchUpdate.localDeletable(model, popup.branch());
    }
}
