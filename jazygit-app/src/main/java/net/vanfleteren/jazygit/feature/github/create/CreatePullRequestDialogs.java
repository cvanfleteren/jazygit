package net.vanfleteren.jazygit.feature.github.create;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;

/**
 * The popups of creating a pull request: whether to push the branch first, and what it merges into and what it says.
 */
public final class CreatePullRequestDialogs {

    /**
     * Asks to push a branch that is not on its remote, or not with all its commits, before creating its pull request.
     */
    public static final ConfirmDialog.Spec<PushFirstPopup> PUSH_FIRST = new ConfirmDialog.Spec<>(
            "push-first",
            PushFirstPopup.class,
            popup -> Messages.get("dialog.pushFirst.title", popup.branch().value()),
            popup -> popup.unpushed()
                    .map(commits -> Messages.get(
                            "dialog.pushFirst.unpushed",
                            popup.branch().value(),
                            commits,
                            popup.target().shortName()))
                    .getOrElse(() -> Messages.get(
                            "dialog.pushFirst.untracked",
                            popup.branch().value(),
                            popup.target().shortName())),
            CreatePullRequestMsg.PushFirstConfirmed::new,
            CreatePullRequestMsg.PushFirstCancelled::new);

    private CreatePullRequestDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningTo(BranchesPanel.ID, new ConfirmDialog<>(PUSH_FIRST, dispatch)),
                PopupSlot.returningTo(BranchesPanel.ID, new NewPullRequestDialog(dispatch)));
    }
}
