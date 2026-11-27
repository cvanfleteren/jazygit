package net.vanfleteren.jazygit.features.github.show;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * A popup that can be opened from the pull request dialog, or from elsewhere, and that follows what changes the dialog
 * it was opened from, like a review that follows whether the pull request is a draft (ADR 100). Unlike a
 * {@link ShowsPullRequest}, it says itself what such a change does to it, so the dialog need not know it.
 */
public interface FollowsPullRequest extends Popup {

    /**
     * The popup showing the pull request it was opened from, to go back to; none when it was opened from elsewhere.
     */
    Option<ShowsPullRequest> from();

    /**
     * {@code model} with this popup following {@code dialog}, the dialog it was opened from as it changed.
     */
    Model following(Model model, PullRequestPopup dialog);
}
