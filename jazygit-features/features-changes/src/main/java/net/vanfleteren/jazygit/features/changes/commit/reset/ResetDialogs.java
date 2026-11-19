package net.vanfleteren.jazygit.features.changes.commit.reset;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.core.git.model.ResetMode;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.kernel.ui.widgets.ConfirmDialog;

/**
 * The popups of resetting the checked out branch.
 */
public final class ResetDialogs {

    /**
     * Asks how to reset the checked out branch to a commit or its upstream, warning about the commits it leaves
     * behind.
     */
    public static final ChoiceDialog.Spec<ResetPopup> RESET = new ChoiceDialog.Spec<>(
            "reset-commit",
            ResetPopup.class,
            popup -> popup.dropped() > 0
                    ? Messages.get(
                            "changes.dialog.reset.titleDropping", popup.target().label(), popup.dropped())
                    : Messages.get("changes.dialog.reset.title", popup.target().label()),
            List.of(
                    new Choice<ResetPopup>(
                                    'm', "changes.dialog.reset.mixed", () -> new ResetMsg.Chosen(ResetMode.MIXED))
                            .withDetails("changes.dialog.reset.mixed.details"),
                    new Choice<ResetPopup>('s', "changes.dialog.reset.soft", () -> new ResetMsg.Chosen(ResetMode.SOFT))
                            .withDetails("changes.dialog.reset.soft.details"),
                    new Choice<ResetPopup>('h', "changes.dialog.reset.hard", () -> new ResetMsg.Chosen(ResetMode.HARD))
                            .withDetails("changes.dialog.reset.hard.details")));

    /**
     * Asks to confirm a hard reset, which discards the uncommitted changes.
     */
    public static final ConfirmDialog.Spec<HardResetPopup> HARD_RESET = ConfirmDialog.Spec.confirming(
            "hard-reset",
            HardResetPopup.class,
            popup -> Messages.get(
                    "changes.dialog.hardReset.title", popup.target().label()),
            popup -> popup.files()
                    .map(files -> Messages.get("changes.dialog.hardReset.message", files))
                    .getOrElse(() -> Messages.get("changes.dialog.hardReset.messageUnknown")));

    private ResetDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                // A reset is asked about from the commits, or from the upstream options of the branches.
                PopupSlot.returningToFocus(new ChoiceDialog<>(RESET, dispatch)),
                PopupSlot.returningToFocus(new ConfirmDialog<>(HARD_RESET, dispatch)));
    }
}
