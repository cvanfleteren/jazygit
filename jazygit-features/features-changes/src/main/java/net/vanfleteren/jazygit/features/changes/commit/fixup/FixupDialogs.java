package net.vanfleteren.jazygit.features.changes.commit.fixup;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.core.git.model.SquashMode;
import net.vanfleteren.jazygit.features.changes.commit.PushedDialogs;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog.Choice;
import net.vanfleteren.jazygit.kernel.ui.widgets.ConfirmDialog;

/**
 * The popups of fixing up a commit.
 */
public final class FixupDialogs {

    /**
     * Asks which message to keep when fixing up a commit into the one below it.
     */
    public static final ChoiceDialog.Spec<FixupPopup> FIXUP = new ChoiceDialog.Spec<>(
            "fixup-commit",
            FixupPopup.class,
            popup -> Messages.get(
                    "changes.dialog.fixup.title",
                    popup.commit().sha().shortSha(),
                    popup.into().shortSha()),
            List.of(
                    new Choice<FixupPopup>(
                                    'f', "changes.dialog.fixup.fixup", () -> new FixupMsg.Chosen(SquashMode.FIXUP))
                            .withDetails("changes.dialog.fixup.fixup.details"),
                    new Choice<FixupPopup>(
                                    'C',
                                    "changes.dialog.fixup.keepMessage",
                                    () -> new FixupMsg.Chosen(SquashMode.FIXUP_KEEP_MESSAGE))
                            .withDetails("changes.dialog.fixup.keepMessage.details")));

    /**
     * Warns that the commit to fix up is pushed already, before asking which message to keep.
     */
    public static final ConfirmDialog.Spec<FixupPushedPopup> PUSHED = new ConfirmDialog.Spec<>(
            "fixup-commit-pushed",
            FixupPushedPopup.class,
            popup -> Messages.get("changes.dialog.pushed.title"),
            popup -> PushedDialogs.goOn(popup.warning()),
            FixupMsg.PushedConfirmed::new);

    private FixupDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningTo(PaneId.COMMITS, new ConfirmDialog<>(PUSHED, dispatch)),
                PopupSlot.returningTo(PaneId.COMMITS, new ChoiceDialog<>(FIXUP, dispatch)));
    }
}
