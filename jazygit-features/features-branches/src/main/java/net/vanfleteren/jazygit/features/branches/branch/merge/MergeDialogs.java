package net.vanfleteren.jazygit.features.branches.branch.merge;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.core.git.model.MergeMode;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog.Choice;

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
                    "branches.dialog.merge.title",
                    popup.branch().name(),
                    popup.into().value()),
            List.of(
                    new Choice<MergePopup>(
                                    'm',
                                    "branches.dialog.merge.fastForward",
                                    () -> new MergeMsg.Chosen(MergeMode.FAST_FORWARD))
                            .withDetails("branches.dialog.merge.fastForward.details"),
                    new Choice<MergePopup>(
                                    'n',
                                    "branches.dialog.merge.mergeCommit",
                                    () -> new MergeMsg.Chosen(MergeMode.MERGE_COMMIT))
                            .withDetails("branches.dialog.merge.mergeCommit.details"),
                    new Choice<MergePopup>(
                                    's', "branches.dialog.merge.squash", () -> new MergeMsg.Chosen(MergeMode.SQUASH))
                            .withDetails("branches.dialog.merge.squash.details"),
                    new Choice<MergePopup>(
                                    'S',
                                    "branches.dialog.merge.squashAndCommit",
                                    () -> new MergeMsg.Chosen(MergeMode.SQUASH_AND_COMMIT))
                            .withDetails("branches.dialog.merge.squashAndCommit.details")));

    private MergeDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(Scopes.BRANCHES.pane(), new ChoiceDialog<>(MERGE, dispatch)));
    }
}
