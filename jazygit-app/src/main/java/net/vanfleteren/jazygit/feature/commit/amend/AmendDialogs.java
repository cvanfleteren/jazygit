package net.vanfleteren.jazygit.feature.commit.amend;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

/**
 * The popups of amending a commit.
 */
public final class AmendDialogs {

    /**
     * Asks to amend the last commit, or another commit, with the staged changes.
     */
    public static final ConfirmDialog.Spec<AmendPopup> AMEND = new ConfirmDialog.Spec<>(
            "amend-commit",
            AmendPopup.class,
            popup -> Messages.get(amendKey(popup.target()) + ".title"),
            popup -> Messages.get(amendKey(popup.target()) + ".message"),
            AmendMsg.Confirmed::new,
            AmendMsg.Cancelled::new);

    /**
     * Asks whether to amend a commit with all files, because none is staged.
     */
    public static final ConfirmDialog.Spec<AmendStageAllPopup> AMEND_STAGE_ALL = new ConfirmDialog.Spec<>(
            "amend-commit-stage-all",
            AmendStageAllPopup.class,
            popup -> Messages.get("dialog.amend.stageAll.title"),
            popup -> Messages.get("dialog.amend.stageAll.message"),
            AmendMsg.StageAllConfirmed::new,
            AmendMsg.Cancelled::new);

    private AmendDialogs() {}

    /**
     * The prefix of the message keys of the amend dialog for {@code target}.
     */
    private static String amendKey(AmendTarget target) {
        return switch (target) {
            case AmendTarget.LastCommit() -> "dialog.amend";
            case AmendTarget.Older(var sha) -> "dialog.amendCommit";
        };
    }

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                // A commit is amended from the files (the last one), or from the commits.
                PopupSlot.returningToFocus(new ConfirmDialog<>(AMEND, dispatch)),
                PopupSlot.returningToFocus(new ConfirmDialog<>(AMEND_STAGE_ALL, dispatch)));
    }
}
