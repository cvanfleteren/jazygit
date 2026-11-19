package net.vanfleteren.jazygit.features.branches.branch.rename;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchesPanel;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.kernel.ui.widgets.TextInputDialog;

/**
 * The popups of renaming a branch.
 */
public final class RenameBranchDialogs {

    /**
     * Asks for the new name of a branch, starting out with the one it has. A blank name keeps it open.
     */
    public static final TextInputDialog.Spec<RenameBranchPopup> RENAME_BRANCH = new TextInputDialog.Spec<>(
                    "rename-branch",
                    RenameBranchPopup.class,
                    popup -> Messages.get(
                            "branches.dialog.renameBranch.title", popup.branch().value()),
                    Option.of(Messages.get("branches.dialog.renameBranch.placeholder")),
                    RenameBranchMsg.Confirmed::new)
            .withWidth(TextInputDialog.BRANCH_NAME_WIDTH);

    /**
     * Asks whether to rename the upstream of a branch on its remote too. Renaming only the local branch comes first,
     * so that it is what a confirmation without a look does: renaming on the remote is seen by everyone.
     */
    public static final ChoiceDialog.Spec<RenameUpstreamPopup> RENAME_UPSTREAM = new ChoiceDialog.Spec<>(
            "rename-upstream",
            RenameUpstreamPopup.class,
            popup -> Messages.get(
                    "branches.dialog.renameUpstream.title",
                    popup.branch().value(),
                    popup.newName().value()),
            List.of(
                    new Choice<RenameUpstreamPopup>(
                                    'l',
                                    popup -> Messages.get(
                                            "branches.dialog.renameUpstream.local",
                                            popup.upstream().shortName()),
                                    RenameBranchMsg.LocallyChosen::new)
                            .withDetails("branches.dialog.renameUpstream.local.details"),
                    new Choice<RenameUpstreamPopup>(
                                    'r',
                                    popup -> Messages.get(
                                            "branches.dialog.renameUpstream.remote",
                                            popup.upstream().shortName(),
                                            popup.renamed().shortName()),
                                    RenameBranchMsg.WithUpstreamChosen::new)
                            .withDetails("branches.dialog.renameUpstream.remote.details")));

    private RenameBranchDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningTo(BranchesPanel.PANE, new TextInputDialog<>(RENAME_BRANCH, dispatch)),
                PopupSlot.returningTo(BranchesPanel.PANE, new ChoiceDialog<>(RENAME_UPSTREAM, dispatch)));
    }
}
