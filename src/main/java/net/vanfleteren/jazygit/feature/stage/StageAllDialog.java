package net.vanfleteren.jazygit.feature.stage;

import net.vanfleteren.jazygit.feature.commit.CommitMsg;
import net.vanfleteren.jazygit.feature.commit.StageAllPopup;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

import java.util.function.Consumer;

/**
 * Popup asking whether to stage all files, because a commit needs something staged.
 */
public final class StageAllDialog {

    public static final String ID = "stage-all";

    private StageAllDialog() {
    }

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public static ConfirmDialog<StageAllPopup> create(Consumer<Msg> dispatch) {
        return new ConfirmDialog<>(ID, StageAllPopup.class,
                popup -> Messages.get("dialog.stageAll.title"),
                popup -> Messages.get("dialog.stageAll.message"),
                CommitMsg.StageAllConfirmed::new, CommitMsg.StageAllCancelled::new, dispatch);
    }
}
