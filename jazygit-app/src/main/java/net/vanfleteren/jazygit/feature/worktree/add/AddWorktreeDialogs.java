package net.vanfleteren.jazygit.feature.worktree.add;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.TextInputDialog;

/**
 * The popup of adding a worktree.
 */
public final class AddWorktreeDialogs {

    /**
     * Asks where the new worktree for a branch goes, starting out next to the main worktree. A blank location
     * keeps it open.
     */
    public static final TextInputDialog.Spec<NewWorktreePopup> NEW_WORKTREE = new TextInputDialog.Spec<>(
            "new-worktree",
            NewWorktreePopup.class,
            popup -> Messages.get("dialog.newWorktree.title", popup.branch().value()),
            Option.of(Messages.get("dialog.newWorktree.placeholder")),
            NewWorktreePopup::location,
            AddWorktreeMsg.Confirmed::new,
            AddWorktreeMsg.Cancelled::new);

    private AddWorktreeDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(BranchesPanel.ID, new TextInputDialog<>(NEW_WORKTREE, dispatch)));
    }
}
