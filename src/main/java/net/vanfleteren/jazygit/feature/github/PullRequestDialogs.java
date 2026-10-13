package net.vanfleteren.jazygit.feature.github;

import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.ui.widgets.PickDialog;

/**
 * The popups of the pull request operations.
 */
public final class PullRequestDialogs {

    /**
     * Asks which branch a new pull request merges into.
     */
    public static final PickDialog.Spec<BasePopup> BASE = new PickDialog.Spec<>(
            "pull-request-base", BasePopup.class,
            popup -> Messages.get("dialog.pullRequestBase.title", popup.branch()),
            BasePopup::bases, PullRequestMsg.BaseChosen::new, PullRequestMsg.BaseCancelled::new);

    private PullRequestDialogs() {
    }
}
