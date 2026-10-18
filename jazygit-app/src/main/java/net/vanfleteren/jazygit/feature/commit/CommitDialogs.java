package net.vanfleteren.jazygit.feature.commit;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.feature.commit.amend.AmendMsg;
import net.vanfleteren.jazygit.feature.commit.amend.AmendPopup;
import net.vanfleteren.jazygit.feature.commit.amendcommit.AmendCommitMsg;
import net.vanfleteren.jazygit.feature.commit.amendcommit.AmendCommitPopup;
import net.vanfleteren.jazygit.feature.commit.amendcommit.AmendCommitStageAllPopup;
import net.vanfleteren.jazygit.feature.commit.create.CommitMsg;
import net.vanfleteren.jazygit.feature.commit.create.StageAllPopup;
import net.vanfleteren.jazygit.feature.commit.drop.DropMsg;
import net.vanfleteren.jazygit.feature.commit.drop.DropPopup;
import net.vanfleteren.jazygit.feature.commit.fixup.FixupMsg;
import net.vanfleteren.jazygit.feature.commit.fixup.FixupPopup;
import net.vanfleteren.jazygit.feature.commit.reset.HardResetPopup;
import net.vanfleteren.jazygit.feature.commit.reset.ResetMsg;
import net.vanfleteren.jazygit.feature.commit.reset.ResetPopup;
import net.vanfleteren.jazygit.feature.commit.squash.SquashMsg;
import net.vanfleteren.jazygit.feature.commit.squash.SquashPopup;
import net.vanfleteren.jazygit.git.model.ResetMode;
import net.vanfleteren.jazygit.git.model.SquashMode;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.CommitsPanel;
import net.vanfleteren.jazygit.ui.FilesPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

/**
 * The confirmations of the commit operations.
 */
public final class CommitDialogs {

    /**
     * Asks to amend the last commit.
     */
    public static final ConfirmDialog.Spec<AmendPopup> AMEND = new ConfirmDialog.Spec<>(
            "amend-commit",
            AmendPopup.class,
            popup -> Messages.get("dialog.amend.title"),
            popup -> Messages.get("dialog.amend.message"),
            AmendMsg.Confirmed::new,
            AmendMsg.Cancelled::new);

    /**
     * Asks to amend a commit with the staged changes.
     */
    public static final ConfirmDialog.Spec<AmendCommitPopup> AMEND_COMMIT = new ConfirmDialog.Spec<>(
            "amend-selected-commit",
            AmendCommitPopup.class,
            popup -> Messages.get("dialog.amendCommit.title"),
            popup -> Messages.get("dialog.amendCommit.message"),
            AmendCommitMsg.Confirmed::new,
            AmendCommitMsg.Cancelled::new);

    /**
     * Asks whether to amend a commit with all files, because none is staged.
     */
    public static final ConfirmDialog.Spec<AmendCommitStageAllPopup> AMEND_COMMIT_STAGE_ALL = new ConfirmDialog.Spec<>(
            "amend-selected-commit-stage-all",
            AmendCommitStageAllPopup.class,
            popup -> Messages.get("dialog.amendCommit.stageAll.title"),
            popup -> Messages.get("dialog.amendCommit.stageAll.message"),
            AmendCommitMsg.StageAllConfirmed::new,
            AmendCommitMsg.Cancelled::new);

    /**
     * Asks to squash a commit into the one below it.
     */
    public static final ConfirmDialog.Spec<SquashPopup> SQUASH = new ConfirmDialog.Spec<>(
            "squash-commit",
            SquashPopup.class,
            popup -> Messages.get("dialog.squash.title"),
            popup -> Messages.get("dialog.squash.message"),
            SquashMsg.Confirmed::new,
            SquashMsg.Cancelled::new);

    /**
     * Asks to drop a commit.
     */
    public static final ConfirmDialog.Spec<DropPopup> DROP = new ConfirmDialog.Spec<>(
            "drop-commit",
            DropPopup.class,
            popup -> Messages.get("dialog.drop.title"),
            popup -> Messages.get(
                    "dialog.drop.message",
                    popup.commit().sha().shortSha(),
                    popup.commit().message()),
            DropMsg.Confirmed::new,
            DropMsg.Cancelled::new);

    /**
     * Asks which message to keep when fixing up a commit into the one below it.
     */
    public static final ChoiceDialog.Spec<FixupPopup> FIXUP = new ChoiceDialog.Spec<>(
            "fixup-commit",
            FixupPopup.class,
            popup -> Messages.get(
                    "dialog.fixup.title",
                    popup.commit().sha().shortSha(),
                    popup.into().sha().shortSha()),
            List.of(
                    new Choice<>('f', "dialog.fixup.fixup", () -> new FixupMsg.Chosen(SquashMode.FIXUP)),
                    new Choice<>(
                            'C', "dialog.fixup.keepMessage", () -> new FixupMsg.Chosen(SquashMode.FIXUP_KEEP_MESSAGE))),
            FixupMsg.Cancelled::new);

    /**
     * Asks how to reset the checked out branch to a commit or its upstream, warning about the commits it leaves
     * behind.
     */
    public static final ChoiceDialog.Spec<ResetPopup> RESET = new ChoiceDialog.Spec<>(
            "reset-commit",
            ResetPopup.class,
            popup -> popup.dropped() > 0
                    ? Messages.get("dialog.reset.titleDropping", popup.target().label(), popup.dropped())
                    : Messages.get("dialog.reset.title", popup.target().label()),
            List.of(
                    new Choice<>('m', "dialog.reset.mixed", () -> new ResetMsg.Chosen(ResetMode.MIXED)),
                    new Choice<>('s', "dialog.reset.soft", () -> new ResetMsg.Chosen(ResetMode.SOFT)),
                    new Choice<>('h', "dialog.reset.hard", () -> new ResetMsg.Chosen(ResetMode.HARD))),
            ResetMsg.Cancelled::new);

    /**
     * Asks to confirm a hard reset, which discards the uncommitted changes.
     */
    public static final ConfirmDialog.Spec<HardResetPopup> HARD_RESET = new ConfirmDialog.Spec<>(
            "hard-reset",
            HardResetPopup.class,
            popup -> Messages.get("dialog.hardReset.title", popup.target().label()),
            popup -> popup.files()
                    .map(files -> Messages.get("dialog.hardReset.message", files))
                    .getOrElse(() -> Messages.get("dialog.hardReset.messageUnknown")),
            ResetMsg.HardConfirmed::new,
            ResetMsg.HardCancelled::new);

    /**
     * Asks whether to stage all files, because a commit needs something staged.
     */
    public static final ConfirmDialog.Spec<StageAllPopup> STAGE_ALL = new ConfirmDialog.Spec<>(
            "stage-all",
            StageAllPopup.class,
            popup -> Messages.get("dialog.stageAll.title"),
            popup -> Messages.get("dialog.stageAll.message"),
            CommitMsg.StageAllConfirmed::new,
            CommitMsg.StageAllCancelled::new);

    private CommitDialogs() {}

    /**
     * The popups of this feature, which send what the user does to {@code dispatch}.
     */
    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningTo(FilesPanel.ID, new ConfirmDialog<>(AMEND, dispatch)),
                PopupSlot.returningTo(FilesPanel.ID, new ConfirmDialog<>(STAGE_ALL, dispatch)),
                PopupSlot.returningTo(FilesPanel.ID, new CommitDialog(dispatch)),
                PopupSlot.returningTo(CommitsPanel.ID, new ConfirmDialog<>(AMEND_COMMIT, dispatch)),
                PopupSlot.returningTo(CommitsPanel.ID, new ConfirmDialog<>(AMEND_COMMIT_STAGE_ALL, dispatch)),
                PopupSlot.returningTo(CommitsPanel.ID, new ConfirmDialog<>(SQUASH, dispatch)),
                PopupSlot.returningTo(CommitsPanel.ID, new ChoiceDialog<>(FIXUP, dispatch)),
                PopupSlot.returningTo(CommitsPanel.ID, new ConfirmDialog<>(DROP, dispatch)),
                // A reset is asked about from the commits, or from the upstream options of the branches.
                PopupSlot.returningToFocus(new ChoiceDialog<>(RESET, dispatch)),
                PopupSlot.returningToFocus(new ConfirmDialog<>(HARD_RESET, dispatch)),
                PopupSlot.returningTo(CommitsPanel.ID, CommitDialog.reword(dispatch)));
    }
}
