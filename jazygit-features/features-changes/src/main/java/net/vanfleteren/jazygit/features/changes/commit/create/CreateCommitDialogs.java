package net.vanfleteren.jazygit.features.changes.commit.create;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.features.changes.commit.CommitDialog;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.widgets.ConfirmDialog;

/**
 * The popups of committing: whether to stage all files, and the commit message.
 */
public final class CreateCommitDialogs {

    /**
     * Asks whether to stage all files, because a commit needs something staged.
     */
    public static final ConfirmDialog.Spec<StageAllPopup> STAGE_ALL = new ConfirmDialog.Spec<>(
            "stage-all",
            StageAllPopup.class,
            popup -> Messages.get("changes.dialog.stageAll.title"),
            popup -> Messages.get("changes.dialog.stageAll.message"),
            CommitMsg.StageAllConfirmed::new);

    private CreateCommitDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningTo(PaneId.FILES, new ConfirmDialog<>(STAGE_ALL, dispatch)),
                PopupSlot.returningTo(PaneId.FILES, new CommitDialog(dispatch)));
    }
}
