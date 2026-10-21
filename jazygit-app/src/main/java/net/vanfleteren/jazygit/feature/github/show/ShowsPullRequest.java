package net.vanfleteren.jazygit.feature.github.show;

import net.vanfleteren.jazygit.state.Popup;

/**
 * A popup that shows a pull request: the pull request dialog itself, or a popup opened from it, which keeps it to go
 * back to. What changes the pull request, like its details being read, changes the dialog wherever it is kept, so
 * going back never shows it out of date.
 */
public interface ShowsPullRequest extends Popup {

    /**
     * The pull request dialog: this one, or the one kept to go back to.
     */
    PullRequestPopup pullRequestPopup();

    /**
     * This popup with {@code dialog} as its pull request dialog.
     */
    ShowsPullRequest withPullRequestPopup(PullRequestPopup dialog);

    /**
     * The number of the pull request.
     */
    default int number() {
        return pullRequestPopup().pullRequest().number();
    }
}
