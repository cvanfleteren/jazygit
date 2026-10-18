package net.vanfleteren.jazygit.feature.branch.newbranch;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.TextInputDialog;

/**
 * The popup of creating a branch.
 */
public final class NewBranchDialogs {

    /**
     * Asks for the name of a new branch. A blank name keeps it open.
     */
    public static final TextInputDialog.Spec<NewBranchPopup> NEW_BRANCH = new TextInputDialog.Spec<>(
            "new-branch-name",
            NewBranchPopup.class,
            popup -> Messages.get("dialog.newBranch.title", popup.label()),
            Option.of(Messages.get("dialog.newBranch.placeholder")),
            NewBranchPopup::name,
            NewBranchMsg.Confirmed::new,
            NewBranchMsg.Cancelled::new);

    private NewBranchDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningToFocus(new TextInputDialog<>(NEW_BRANCH, dispatch)));
    }
}
