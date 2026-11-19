package net.vanfleteren.jazygit.features.github.create;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.features.branches.branch.stack.StackPopups;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchesPanel;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.widgets.ConfirmDialog;

/**
 * The popups of creating a pull request: whether to create it to put it on a stack, whether to push the branch first,
 * and what it merges into and what it says.
 */
public final class CreatePullRequestDialogs {

    /**
     * Asks to push a branch that is not on its remote, or not with all its commits, before creating its pull request.
     */
    public static final ConfirmDialog.Spec<PushFirstPopup> PUSH_FIRST = new ConfirmDialog.Spec<>(
            "push-first",
            PushFirstPopup.class,
            popup ->
                    Messages.get("github.dialog.pushFirst.title", popup.branch().value()),
            popup -> popup.unpushed()
                    .map(commits -> Messages.get(
                            "github.dialog.pushFirst.unpushed",
                            commits,
                            popup.target().shortName()))
                    .getOrElse(() -> Messages.get(
                            "github.dialog.pushFirst.untracked", popup.target().shortName())),
            CreatePullRequestMsg.PushFirstConfirmed::new);

    /**
     * Asks to create the pull request of a branch without one, to put it on a stack (ADR 148): the stack menu opens it,
     * in {@code branches}, which cannot see creating a pull request.
     */
    public static final ConfirmDialog.Spec<StackPopups.CreateFirst> CREATE_FIRST = new ConfirmDialog.Spec<>(
            "stack-create-first",
            StackPopups.CreateFirst.class,
            popup -> popup.stack()
                    .map(stack -> Messages.get(
                            "github.dialog.createFirst.title", popup.branch().value(), String.valueOf(stack)))
                    .getOrElse(() -> Messages.get(
                            "github.dialog.createFirst.titleNew",
                            popup.branch().value(),
                            String.valueOf(popup.below().value()))),
            CreatePullRequestDialogs::createFirst,
            CreatePullRequestMsg.StackRequested::new);

    private CreatePullRequestDialogs() {}

    /**
     * What confirming {@code popup} does: the pull request it creates, and where that goes.
     */
    static String createFirst(StackPopups.CreateFirst popup) {
        String branch = popup.branch().value();
        String base = popup.base().value();
        return popup.stack()
                .map(stack -> Messages.get("github.dialog.createFirst.onTop", branch, base, String.valueOf(stack)))
                .getOrElse(() -> Messages.get(
                        "github.dialog.createFirst.onPullRequest",
                        branch,
                        base,
                        String.valueOf(popup.below().value())));
    }

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningTo(BranchesPanel.PANE, new ConfirmDialog<>(PUSH_FIRST, dispatch)),
                PopupSlot.returningToFocus(new ConfirmDialog<>(CREATE_FIRST, dispatch)),
                PopupSlot.returningTo(BranchesPanel.PANE, new NewPullRequestDialog(dispatch)));
    }
}
