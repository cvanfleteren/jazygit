package net.vanfleteren.jazygit.feature.forcedelete;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

/**
 * Warns that deleting a branch that is not merged loses the commits only it has, and asks to delete it anyway.
 */
public final class ForceDeleteDialog {

    public static final ConfirmDialog.Spec<ForceDeleteBranchPopup> SPEC = new ConfirmDialog.Spec<>(
            "force-delete-branch",
            ForceDeleteBranchPopup.class,
            popup -> Messages.get("dialog.deleteBranch.title", popup.branch().value()),
            popup -> Messages.get(
                    popup.gone() ? "dialog.forceDeleteBranch.gone" : "dialog.forceDeleteBranch.message",
                    popup.lost(),
                    popup.branch().value()),
            ForceDeleteMsg.Confirmed::new,
            ForceDeleteMsg.Cancelled::new);

    private ForceDeleteDialog() {}

    /**
     * The popups of this feature, which send what the user does to {@code dispatch}.
     */
    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(BranchesPanel.ID, new ConfirmDialog<>(SPEC, dispatch)));
    }
}
