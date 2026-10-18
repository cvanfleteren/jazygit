package net.vanfleteren.jazygit.feature.commit.reset;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.git.model.ResetMode;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

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

    private ResetDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                // A reset is asked about from the commits, or from the upstream options of the branches.
                PopupSlot.returningToFocus(new ChoiceDialog<>(RESET, dispatch)),
                PopupSlot.returningToFocus(new ConfirmDialog<>(HARD_RESET, dispatch)));
    }
}
