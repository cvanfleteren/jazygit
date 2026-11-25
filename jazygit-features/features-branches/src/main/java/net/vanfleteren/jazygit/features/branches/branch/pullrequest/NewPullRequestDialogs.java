package net.vanfleteren.jazygit.features.branches.branch.pullrequest;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.widgets.ConfirmDialog;

/**
 * The popups of creating a pull request that are the same on every forge: whether to push the branch first (ADR 25).
 */
public final class NewPullRequestDialogs {

    /**
     * Asks to push a branch that is not on its remote, or not with all its commits, before creating its pull request.
     */
    public static final ConfirmDialog.Spec<PushFirstPopup> PUSH_FIRST = new ConfirmDialog.Spec<>(
            "push-first",
            PushFirstPopup.class,
            popup -> Messages.get(
                    "branches.dialog.pushFirst.title", popup.branch().value()),
            popup -> popup.unpushed()
                    .map(commits -> Messages.get(
                            "branches.dialog.pushFirst.unpushed",
                            commits,
                            popup.target().shortName()))
                    .getOrElse(() -> Messages.get(
                            "branches.dialog.pushFirst.untracked",
                            popup.target().shortName())),
            NewPullRequestMsg.PushFirstConfirmed::new);

    private NewPullRequestDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(Scopes.BRANCHES.pane(), new ConfirmDialog<>(PUSH_FIRST, dispatch)));
    }
}
