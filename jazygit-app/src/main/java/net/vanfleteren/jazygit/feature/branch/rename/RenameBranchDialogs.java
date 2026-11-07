package net.vanfleteren.jazygit.feature.branch.rename;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.ui.widgets.TextInputDialog;

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
                            "dialog.renameBranch.title", popup.branch().value()),
                    Option.of(Messages.get("dialog.renameBranch.placeholder")),
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
                    "dialog.renameUpstream.title",
                    popup.branch().value(),
                    popup.newName().value()),
            List.of(
                    new Choice<>(
                            'l',
                            popup -> Messages.get(
                                    "dialog.renameUpstream.local",
                                    popup.upstream().shortName()),
                            RenameBranchMsg.LocallyChosen::new),
                    new Choice<>(
                            'r',
                            popup -> Messages.get(
                                    "dialog.renameUpstream.remote",
                                    popup.upstream().shortName(),
                                    popup.renamed().shortName()),
                            RenameBranchMsg.WithUpstreamChosen::new)));

    private RenameBranchDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningTo(BranchesPanel.ID, new TextInputDialog<>(RENAME_BRANCH, dispatch)),
                PopupSlot.returningTo(BranchesPanel.ID, new ChoiceDialog<>(RENAME_UPSTREAM, dispatch)));
    }
}
