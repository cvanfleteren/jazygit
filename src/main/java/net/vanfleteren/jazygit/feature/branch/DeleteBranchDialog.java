package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.feature.branch.BranchCmd.DeleteBranch.DeleteScope;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;

import java.util.List;
import java.util.function.Consumer;

/**
 * Popup asking where to delete a branch. The options that act on the remote only apply when the branch has
 * a remote branch that can be deleted.
 */
public final class DeleteBranchDialog {

    public static final String ID = "delete-branch";

    private DeleteBranchDialog() {
    }

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public static ChoiceDialog<DeleteBranchPopup> create(Consumer<Msg> dispatch) {
        return new ChoiceDialog<>(ID, DeleteBranchPopup.class,
                popup -> Messages.get("dialog.deleteBranch.title", popup.branch()),
                List.of(new Choice<>('d', "dialog.deleteBranch.local", () -> new DeleteBranchMsg.Chosen(DeleteScope.LOCAL)),
                        new Choice<>('r', "dialog.deleteBranch.remote", () -> new DeleteBranchMsg.Chosen(DeleteScope.REMOTE),
                                DeleteBranchDialog::remoteDeletable),
                        new Choice<>('b', "dialog.deleteBranch.both", () -> new DeleteBranchMsg.Chosen(DeleteScope.BOTH),
                                DeleteBranchDialog::remoteDeletable)),
                DeleteBranchMsg.Cancelled::new, dispatch);
    }

    private static boolean remoteDeletable(Model model, DeleteBranchPopup popup) {
        return model.branches() instanceof Loadable.Loaded<List<Branch>>(List<Branch> branches)
                && branches.stream().anyMatch(b -> b.name().equals(popup.branch()) && b.remoteDeletable());
    }
}
