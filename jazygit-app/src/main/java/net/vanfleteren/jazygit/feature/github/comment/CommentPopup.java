package net.vanfleteren.jazygit.feature.github.comment;

import lombok.With;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.feature.github.show.ShowsPullRequest;

/**
 * Asking for a comment on the pull request that {@code from} shows. Only one popup is open at a time, so this one keeps
 * the popup it was opened from, the pull request dialog or the comments, to go back to.
 *
 * @param from    the popup it was opened from, as it was then
 * @param text    the comment as far as it was typed, or as it came back from the editor
 * @param sending whether it is being sent; it can then not be changed, nor sent again
 */
@With
public record CommentPopup(ShowsPullRequest from, String text, boolean sending) implements ShowsPullRequest {

    /**
     * The popup asking for a comment on the pull request {@code from} shows, which starts out empty.
     */
    public static CommentPopup on(ShowsPullRequest from) {
        return new CommentPopup(from, "", false);
    }

    @Override
    public PullRequestPopup pullRequestPopup() {
        return from.pullRequestPopup();
    }

    @Override
    public CommentPopup withPullRequestPopup(PullRequestPopup dialog) {
        return withFrom(from.withPullRequestPopup(dialog));
    }
}
