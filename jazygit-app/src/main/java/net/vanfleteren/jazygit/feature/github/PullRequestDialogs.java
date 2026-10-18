package net.vanfleteren.jazygit.feature.github;

import io.vavr.collection.List;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.PopupSlot;
import net.vanfleteren.jazygit.ui.widgets.PickDialog;

/**
 * The popups of the pull request operations.
 */
public final class PullRequestDialogs {

    /**
     * Asks which branch a new pull request merges into.
     */
    public static final PickDialog.Spec<BasePopup, String> BASE = new PickDialog.Spec<>(
            "pull-request-base",
            BasePopup.class,
            popup -> Messages.get("dialog.pullRequestBase.title", popup.branch().value()),
            popup -> popup.bases().map(Branch.Name::value).map(PickDialog.Row::of),
            base -> new PullRequestMsg.BaseChosen(new Branch.Name(base)),
            PullRequestMsg.BaseCancelled::new);

    private PullRequestDialogs() {}

    /**
     * The popups of this feature, which send what the user does to {@code dispatch}.
     */
    public static List<PopupSlot> popups(Consumer<Msg> dispatch) {
        return List.of(
                PopupSlot.returningTo(BranchesPanel.ID, new PickDialog<>(BASE, dispatch)),
                // A pull request is shown from the branches, or from the pull requests screen.
                PopupSlot.returningToFocus(new PullRequestDialog(dispatch)),
                PopupSlot.returningTo(BranchesPanel.ID, new NewPullRequestDialog(dispatch)));
    }
}
