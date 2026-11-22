package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.widgets.ConfirmDialog;

/**
 * The popups of creating a pull request on Forgejo (ADR 178): whether to push the branch first, and what it merges
 * into and what it says.
 */
public final class CreatePullRequestDialogs {

    /**
     * Asks to push a branch that is not on its remote, or not with all its commits, before creating its pull request
     * (ADR 25).
     */
    public static final ConfirmDialog.Spec<PushFirstPopup> PUSH_FIRST = new ConfirmDialog.Spec<>(
            "forgejo-push-first",
            PushFirstPopup.class,
            popup -> Messages.get(
                    "forgejo.dialog.pushFirst.title", popup.branch().value()),
            popup -> popup.unpushed()
                    .map(commits -> Messages.get(
                            "forgejo.dialog.pushFirst.unpushed",
                            commits,
                            popup.target().shortName()))
                    .getOrElse(() -> Messages.get(
                            "forgejo.dialog.pushFirst.untracked", popup.target().shortName())),
            CreatePullRequestMsg.PushFirstConfirmed::new);

    private CreatePullRequestDialogs() {}

    static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningTo(Scopes.BRANCHES.pane(), new ConfirmDialog<>(PUSH_FIRST, dispatch)),
                PopupSlot.returningTo(Scopes.BRANCHES.pane(), new ForgejoNewPullRequestDialog(dispatch)));
    }
}
