package net.vanfleteren.jazygit.features.branches.branch.pull;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.core.git.model.PullMode;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog.Choice;

/**
 * The popup of pulling a diverged branch.
 */
public final class PullDialogs {

    /**
     * Asks how to pull the checked out branch, which has diverged from its upstream: by rebasing or by merging.
     */
    public static final ChoiceDialog.Spec<PullPopup> PULL = new ChoiceDialog.Spec<>(
            "pull",
            PullPopup.class,
            popup -> Messages.get("branches.dialog.pull.title", popup.branch().value()),
            List.of(
                    new Choice<PullPopup>('r', "branches.dialog.pull.rebase", () -> new PullMsg.Chosen(PullMode.REBASE))
                            .withDetails("branches.dialog.pull.rebase.details"),
                    new Choice<PullPopup>('m', "branches.dialog.pull.merge", () -> new PullMsg.Chosen(PullMode.MERGE))
                            .withDetails("branches.dialog.pull.merge.details")));

    private PullDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(PopupSlot.returningTo(Scopes.BRANCHES.pane(), new ChoiceDialog<>(PULL, dispatch)));
    }
}
