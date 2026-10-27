package net.vanfleteren.jazygit.feature.branch.merge;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.git.model.MergeMode;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;

/**
 * The popup of merging a branch.
 */
public final class MergeDialogs {

    /**
     * Asks how to merge the highlighted branch into the checked out one.
     */
    public static final ChoiceDialog.Spec<MergePopup> MERGE = new ChoiceDialog.Spec<>(
            "merge",
            MergePopup.class,
            popup -> Messages.get(
                    "dialog.merge.title", popup.branch().value(), popup.into().value()),
            List.of(
                    new Choice<>('m', "dialog.merge.fastForward", () -> new MergeMsg.Chosen(MergeMode.FAST_FORWARD)),
                    new Choice<>('n', "dialog.merge.mergeCommit", () -> new MergeMsg.Chosen(MergeMode.MERGE_COMMIT)),
                    new Choice<>('s', "dialog.merge.squash", () -> new MergeMsg.Chosen(MergeMode.SQUASH)),
                    new Choice<>(
                            'S',
                            "dialog.merge.squashAndCommit",
                            () -> new MergeMsg.Chosen(MergeMode.SQUASH_AND_COMMIT))));

    private MergeDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(BranchesPanel.ID, new ChoiceDialog<>(MERGE, dispatch)));
    }
}
