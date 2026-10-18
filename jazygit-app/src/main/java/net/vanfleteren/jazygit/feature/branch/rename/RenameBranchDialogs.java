package net.vanfleteren.jazygit.feature.branch.rename;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.TextInputDialog;

/**
 * The popup of renaming a branch.
 */
public final class RenameBranchDialogs {

    /**
     * Asks for the new name of a branch, starting out with the one it has. A blank name keeps it open.
     */
    public static final TextInputDialog.Spec<RenameBranchPopup> RENAME_BRANCH = new TextInputDialog.Spec<>(
            "rename-branch",
            RenameBranchPopup.class,
            popup -> Messages.get("dialog.renameBranch.title", popup.branch().value()),
            Option.of(Messages.get("dialog.renameBranch.placeholder")),
            popup -> popup.branch().value(),
            RenameBranchMsg.Confirmed::new,
            RenameBranchMsg.Cancelled::new);

    private RenameBranchDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(BranchesPanel.ID, new TextInputDialog<>(RENAME_BRANCH, dispatch)));
    }
}
