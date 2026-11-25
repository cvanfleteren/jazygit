package net.vanfleteren.jazygit.features.midway.inprogress;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.core.git.model.InProgressAction;
import net.vanfleteren.jazygit.core.repo.InProgress;
import net.vanfleteren.jazygit.features.midway.plan.content.PlanRows;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.text.InProgressText;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.kernel.ui.widgets.ConfirmDialog;

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
            popup -> Messages.get("midway.dialog.inProgress.title", InProgressText.kind(popup.inProgress())),
            List.of(
                    new Choice<InProgressPopup>(
                                    'c',
                                    popup -> popup.conflicts() > 0
                                            ? Messages.get(
                                                    "midway.dialog.inProgress.continueConflicts", popup.conflicts())
                                            : popup.marked().isEmpty()
                                                    ? Messages.get("midway.dialog.inProgress.continue")
                                                    : Messages.get(
                                                            "midway.dialog.inProgress.continueMarked",
                                                            popup.marked().size()),
                                    () -> new InProgressMsg.Chosen(InProgressAction.CONTINUE))
                            .withDetails(InProgressDialogs::continueDetails),
                    new Choice<InProgressPopup>(
                                    'a',
                                    "midway.dialog.inProgress.abort",
                                    () -> new InProgressMsg.Chosen(InProgressAction.ABORT))
                            .withDetails("midway.dialog.inProgress.abort.details"),
                    new Choice<InProgressPopup>(
                                    's',
                                    "midway.dialog.inProgress.skip",
                                    () -> new InProgressMsg.Chosen(InProgressAction.SKIP),
                                    (model, popup) -> popup.inProgress().skippable())
                            .withDetails(popup ->
                                    where(popup).map(where -> where + " ").getOrElse("")
                                            + Messages.get("midway.dialog.inProgress.skip.details"))));

    /**
     * Asks to confirm aborting or skipping, which throws work away.
     */
    public static final ConfirmDialog.Spec<InProgressConfirmPopup> CONFIRM = ConfirmDialog.Spec.confirming(
            "in-progress-confirm",
            InProgressConfirmPopup.class,
            popup -> Messages.get(
                    popup.action() == InProgressAction.SKIP
                            ? "midway.dialog.inProgress.skipTitle"
                            : "midway.dialog.inProgress.abortTitle",
                    InProgressText.kind(popup.inProgress())),
            popup -> Messages.get(
                    popup.action() == InProgressAction.SKIP
                            ? "midway.dialog.inProgress.skipMessage"
                            : "midway.dialog.inProgress.abortMessage",
                    InProgressText.kind(popup.inProgress())));

    private InProgressDialogs() {}

    /**
     * What continuing does; and, when files resolved since it stopped still have conflict markers, that it commits
     * them (ADR 198).
     */
    private static String continueDetails(InProgressPopup popup) {
        String does = where(popup).map(where -> where + " ").getOrElse("")
                + popup.plan()
                        .flatMap(plan -> plan.todo().headOption())
                        .map(next -> Messages.get("midway.dialog.inProgress.next", PlanRows.named(next)) + " ")
                        .getOrElse("")
                + Messages.get(continueDetailsKey(popup.inProgress()));
        return popup.marked().isEmpty()
                ? does
                : Messages.get(
                                "midway.dialog.inProgress.marked",
                                popup.marked().mkString(", "),
                                popup.marked().size())
                        + " " + does;
    }

    /**
     * Where it stopped, as its steps say (ADR 199): {@code Stopped at 2 of 3: 1466668 feat touches f.}; nothing for an
     * operation whose steps git keeps no list of, as a merge.
     */
    private static Option<String> where(InProgressPopup popup) {
        return popup.plan()
                .map(plan -> plan.total() > 1
                        ? Messages.get(
                                "midway.dialog.inProgress.stoppedAt",
                                plan.step(),
                                plan.total(),
                                PlanRows.named(plan.stopped()))
                        : Messages.get("midway.dialog.inProgress.stoppedAtOnly", PlanRows.named(plan.stopped())));
    }

    /**
     * The message key of what continuing does: only a rebase has a next commit to go on with.
     */
    private static String continueDetailsKey(InProgress inProgress) {
        return switch (inProgress) {
            case InProgress.Merging m -> "midway.dialog.inProgress.continue.merge.details";
            case InProgress.Rebasing r -> "midway.dialog.inProgress.continue.rebase.details";
            case InProgress.CherryPicking c -> "midway.dialog.inProgress.continue.cherryPick.details";
            case InProgress.Reverting r -> "midway.dialog.inProgress.continue.revert.details";
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
