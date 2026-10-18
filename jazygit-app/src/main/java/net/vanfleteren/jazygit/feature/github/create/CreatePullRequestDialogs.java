package net.vanfleteren.jazygit.feature.github.create;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.PickDialog;

/**
 * The popups of creating a pull request: which branch it merges into, and what it says.
 */
public final class CreatePullRequestDialogs {

    /**
     * Asks which branch a new pull request merges into.
     */
    public static final PickDialog.Spec<BasePopup, String> BASE = new PickDialog.Spec<>(
            "pull-request-base",
            BasePopup.class,
            popup -> Messages.get("dialog.pullRequestBase.title", popup.branch().value()),
            popup -> popup.bases().map(Branch.Name::value).map(PickDialog.Row::of),
            base -> new CreatePullRequestMsg.BaseChosen(new Branch.Name(base)),
            CreatePullRequestMsg.BaseCancelled::new);

    private CreatePullRequestDialogs() {}

    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningTo(BranchesPanel.ID, new PickDialog<>(BASE, dispatch)),
                PopupSlot.returningTo(BranchesPanel.ID, new NewPullRequestDialog(dispatch)));
    }
}
