package net.vanfleteren.jazygit.feature.branch.rebase;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;

/**
 * The popup of rebasing onto a branch.
 */
public final class RebaseDialogs {

    /**
     * Asks how to rebase the checked out branch onto the highlighted one.
     */
    public static final ChoiceDialog.Spec<RebasePopup> REBASE = new ChoiceDialog.Spec<>(
            "rebase",
            RebasePopup.class,
            popup -> Messages.get("dialog.rebase.title", popup.branch().value()),
            List.of(new Choice<RebasePopup>(
                    's',
                    popup -> Messages.get("dialog.rebase.simple", popup.onto().value()),
                    () -> new Msg.PopupConfirmed(RebasePopup.class))));

    private RebaseDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(BranchesPanel.ID, new ChoiceDialog<>(REBASE, dispatch)));
    }
}
