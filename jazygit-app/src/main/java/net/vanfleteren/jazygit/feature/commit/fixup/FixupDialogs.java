package net.vanfleteren.jazygit.feature.commit.fixup;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.git.model.SquashMode;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.CommitsPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
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
                    "dialog.fixup.title",
                    popup.commit().sha().shortSha(),
                    popup.into().shortSha()),
            List.of(
                    new Choice<>('f', "dialog.fixup.fixup", () -> new FixupMsg.Chosen(SquashMode.FIXUP)),
                    new Choice<>(
                            'C', "dialog.fixup.keepMessage", () -> new FixupMsg.Chosen(SquashMode.FIXUP_KEEP_MESSAGE))),
            FixupMsg.Cancelled::new);

    private FixupDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(CommitsPanel.ID, new ChoiceDialog<>(FIXUP, dispatch)));
    }
}
