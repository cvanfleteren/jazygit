package net.vanfleteren.jazygit.feature.commit.create;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.feature.commit.CommitDialog;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.FilesPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

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
            popup -> Messages.get("dialog.stageAll.title"),
            popup -> Messages.get("dialog.stageAll.message"),
            CommitMsg.StageAllConfirmed::new);

    private CreateCommitDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningTo(FilesPanel.ID, new ConfirmDialog<>(STAGE_ALL, dispatch)),
                PopupSlot.returningTo(FilesPanel.ID, new CommitDialog(dispatch)));
    }
}
