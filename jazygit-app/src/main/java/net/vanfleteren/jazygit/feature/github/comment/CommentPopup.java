package net.vanfleteren.jazygit.feature.github.comment;

import lombok.With;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking for a comment on the pull request {@code shown}. Only one popup is open at a time, so this one keeps the
 * pull request dialog it was opened from, to go back to.
 *
 * @param shown   the pull request dialog, as it was when the comment was asked for
 * @param text    the comment as far as it was typed, or as it came back from the editor
 * @param sending whether it is being sent; it can then not be changed, nor sent again
 */
@With
public record CommentPopup(PullRequestPopup shown, String text, boolean sending) implements Popup {

    /**
     * The popup asking for a comment on the pull request {@code shown}, which starts out empty.
     */
    public static CommentPopup on(PullRequestPopup shown) {
        return new CommentPopup(shown, "", false);
    }

    /**
     * The number of the pull request the comment is on.
     */
    public int number() {
        return shown.pullRequest().number();
    }
}
