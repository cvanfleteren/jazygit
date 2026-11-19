package net.vanfleteren.jazygit.features.branches.branch.nuke;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchesPanel;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.widgets.ConfirmDialog;

/**
 * The popup of nuking a branch.
 */
public final class NukeBranchDialogs {

    /**
     * Asks to confirm nuking a branch, sternly: it names all that goes, and that what only that has is lost.
     */
    public static final ConfirmDialog.Spec<NukeBranchPopup> NUKE_BRANCH = new ConfirmDialog.Spec<>(
            "nuke-branch",
            NukeBranchPopup.class,
            popup -> Messages.get(
                    "branches.dialog.nukeBranch.title", popup.branch().value()),
            NukeBranchDialogs::message,
            NukeBranchMsg.Confirmed::new);

    private NukeBranchDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(BranchesPanel.PANE, new ConfirmDialog<>(NUKE_BRANCH, dispatch)));
    }

    private static String message(NukeBranchPopup popup) {
        return Messages.get(
                "branches.dialog.nukeBranch.message",
                List.of(Messages.get(
                                "branches.dialog.nukeBranch.branch",
                                popup.branch().value()))
                        .appendAll(popup.remote()
                                .map(remote -> Messages.get("branches.dialog.nukeBranch.remote", remote.shortName())))
                        .appendAll(popup.worktree()
                                .map(worktree -> Messages.get(
                                        worktree.isLocked()
                                                ? "branches.dialog.nukeBranch.worktreeLocked"
                                                : "branches.dialog.nukeBranch.worktree",
                                        worktree.location())))
                        .mkString("\n"));
    }
}
