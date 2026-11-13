package net.vanfleteren.jazygit.feature.inprogress;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.git.model.InProgressAction;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.text.InProgressText;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

/**
 * The choice of what to do with the operation the repository is in the middle of, and the confirmation of
 * aborting or skipping it.
 */
public final class InProgressDialogs {

    /**
     * Asks whether to continue, abort or skip. Continuing is offered while conflicts remain too, to tell how many
     * are left; skipping is not possible for a merge.
     */
    public static final ChoiceDialog.Spec<InProgressPopup> CHOICE = new ChoiceDialog.Spec<>(
            "in-progress",
            InProgressPopup.class,
            popup -> Messages.get("dialog.inProgress.title", InProgressText.kind(popup.inProgress())),
            List.of(
                    new Choice<InProgressPopup>(
                                    'c',
                                    popup -> popup.conflicts() > 0
                                            ? Messages.get("dialog.inProgress.continueConflicts", popup.conflicts())
                                            : Messages.get("dialog.inProgress.continue"),
                                    () -> new InProgressMsg.Chosen(InProgressAction.CONTINUE))
                            .withDetails(popup -> Messages.get(continueDetailsKey(popup.inProgress()))),
                    new Choice<InProgressPopup>(
                                    'a',
                                    "dialog.inProgress.abort",
                                    () -> new InProgressMsg.Chosen(InProgressAction.ABORT))
                            .withDetails("dialog.inProgress.abort.details"),
                    new Choice<InProgressPopup>(
                                    's',
                                    "dialog.inProgress.skip",
                                    () -> new InProgressMsg.Chosen(InProgressAction.SKIP),
                                    (model, popup) -> popup.inProgress().skippable())
                            .withDetails("dialog.inProgress.skip.details")));

    /**
     * Asks to confirm aborting or skipping, which throws work away.
     */
    public static final ConfirmDialog.Spec<InProgressConfirmPopup> CONFIRM = ConfirmDialog.Spec.confirming(
            "in-progress-confirm",
            InProgressConfirmPopup.class,
            popup -> Messages.get(
                    popup.action() == InProgressAction.SKIP
                            ? "dialog.inProgress.skipTitle"
                            : "dialog.inProgress.abortTitle",
                    InProgressText.kind(popup.inProgress())),
            popup -> Messages.get(
                    popup.action() == InProgressAction.SKIP
                            ? "dialog.inProgress.skipMessage"
                            : "dialog.inProgress.abortMessage",
                    InProgressText.kind(popup.inProgress())));

    private InProgressDialogs() {}

    /**
     * The message key of what continuing does: only a rebase has a next commit to go on with.
     */
    private static String continueDetailsKey(InProgress inProgress) {
        return switch (inProgress) {
            case InProgress.Merging m -> "dialog.inProgress.continue.merge.details";
            case InProgress.Rebasing r -> "dialog.inProgress.continue.rebase.details";
            case InProgress.CherryPicking c -> "dialog.inProgress.continue.cherryPick.details";
            case InProgress.Reverting r -> "dialog.inProgress.continue.revert.details";
        };
    }

    /**
     * The popups of this feature, which send what the user does to {@code dispatch}.
     */
    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                // What is in progress is asked about from anywhere.
                PopupSlot.returningToFocus(new ChoiceDialog<>(CHOICE, dispatch)),
                PopupSlot.returningToFocus(new ConfirmDialog<>(CONFIRM, dispatch)));
    }
}
