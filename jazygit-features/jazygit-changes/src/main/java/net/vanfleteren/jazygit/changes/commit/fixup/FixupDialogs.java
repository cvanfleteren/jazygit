package net.vanfleteren.jazygit.changes.commit.fixup;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.git.model.SquashMode;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.commits.CommitsPanel;
import net.vanfleteren.jazygit.ui.i18n.Messages;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;

/**
 * The popup of fixing up a commit.
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

    private FixupDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(CommitsPanel.PANE, new ChoiceDialog<>(FIXUP, dispatch)));
    }
}
