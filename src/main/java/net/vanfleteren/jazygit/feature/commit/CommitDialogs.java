package net.vanfleteren.jazygit.feature.commit;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.SquashMode;
import net.vanfleteren.jazygit.i18n.Messages;
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
            "amend-commit", AmendPopup.class,
            popup -> Messages.get("dialog.amend.title"),
            popup -> Messages.get("dialog.amend.message"),
            AmendMsg.Confirmed::new, AmendMsg.Cancelled::new);

    /**
     * Asks to squash a commit into the one below it.
     */
    public static final ConfirmDialog.Spec<SquashPopup> SQUASH = new ConfirmDialog.Spec<>(
            "squash-commit", SquashPopup.class,
            popup -> Messages.get("dialog.squash.title"),
            popup -> Messages.get("dialog.squash.message"),
            SquashMsg.Confirmed::new, SquashMsg.Cancelled::new);

    /**
     * Asks which message to keep when fixing up a commit into the one below it.
     */
    public static final ChoiceDialog.Spec<FixupPopup> FIXUP = new ChoiceDialog.Spec<>(
            "fixup-commit", FixupPopup.class,
            popup -> Messages.get("dialog.fixup.title", popup.commit().sha().shortSha(), popup.into().sha().shortSha()),
            List.of(new Choice<>('f', "dialog.fixup.fixup", () -> new FixupMsg.Chosen(SquashMode.FIXUP)),
                    new Choice<>('C', "dialog.fixup.keepMessage", () -> new FixupMsg.Chosen(SquashMode.FIXUP_KEEP_MESSAGE))),
            FixupMsg.Cancelled::new);

    /**
     * Asks whether to stage all files, because a commit needs something staged.
     */
    public static final ConfirmDialog.Spec<StageAllPopup> STAGE_ALL = new ConfirmDialog.Spec<>(
            "stage-all", StageAllPopup.class,
            popup -> Messages.get("dialog.stageAll.title"),
            popup -> Messages.get("dialog.stageAll.message"),
            CommitMsg.StageAllConfirmed::new, CommitMsg.StageAllCancelled::new);

    private CommitDialogs() {
    }
}
