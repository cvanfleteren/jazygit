package net.vanfleteren.jazygit.features.branches.forcedelete;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchesPanel;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.widgets.ConfirmDialog;

/**
 * Warns that deleting a branch that is not merged loses the commits only it has, and asks to delete it anyway.
 */
public final class ForceDeleteDialog {

    public static final ConfirmDialog.Spec<ForceDeleteBranchPopup> SPEC = new ConfirmDialog.Spec<>(
            "force-delete-branch",
            ForceDeleteBranchPopup.class,
            popup -> Messages.get(
                    "branches.dialog.deleteBranch.title", popup.branch().value()),
            popup -> Messages.get(
                    popup.gone()
                            ? "branches.dialog.forceDeleteBranch.gone"
                            : "branches.dialog.forceDeleteBranch.message",
                    popup.lost(),
                    popup.branch().value()),
            ForceDeleteMsg.Confirmed::new);

    private ForceDeleteDialog() {}

    /**
     * The popups of this feature, which send what the user does to {@code dispatch}.
     */
    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(BranchesPanel.PANE, new ConfirmDialog<>(SPEC, dispatch)));
    }
}
