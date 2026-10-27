package net.vanfleteren.jazygit.feature.branch.push;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;
import net.vanfleteren.jazygit.ui.widgets.TextInputDialog;

/**
 * The popups of pushing a branch: where to, and whether to force it.
 */
public final class PushDialogs {

    /**
     * Asks where to push a branch, as {@code <remote> <branch>}: one without an upstream, or one that
     * {@code push.default} did not push, which the title then says. It starts out with the branch of the same name.
     * What cannot be read that way keeps it open.
     */
    public static final TextInputDialog.Spec<SetUpstreamPopup> SET_UPSTREAM = new TextInputDialog.Spec<>(
            "set-upstream",
            SetUpstreamPopup.class,
            popup -> popup.refused()
                    .map(refused -> Messages.get(
                            "dialog.setUpstream.refusedTitle",
                            popup.branch().value(),
                            refused.upstream().shortName(),
                            refused.pushDefault()))
                    .getOrElse(() -> Messages.get("dialog.setUpstream.title")),
            Option.none(),
            PushMsg.UpstreamConfirmed::new);

    /**
     * Warns that a branch has diverged from its remote branch, and asks to force push it.
     */
    public static final ConfirmDialog.Spec<ForcePushPopup> FORCE_PUSH = new ConfirmDialog.Spec<>(
            "force-push",
            ForcePushPopup.class,
            popup -> Messages.get("dialog.forcePush.title", popup.branch().value()),
            popup -> Messages.get("dialog.forcePush.message"),
            PushMsg.ForceConfirmed::new);

    private PushDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningTo(BranchesPanel.ID, new ConfirmDialog<>(FORCE_PUSH, dispatch)),
                PopupSlot.returningTo(BranchesPanel.ID, new TextInputDialog<>(SET_UPSTREAM, dispatch)));
    }
}
