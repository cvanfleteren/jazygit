package net.vanfleteren.jazygit.feature.github.comments;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.feature.github.show.ShowsPullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequestComment;
import net.vanfleteren.jazygit.state.Failure;

/**
 * Showing the conversation on the pull request of the dialog {@code shown}, which it was opened from and goes back
 * to.
 *
 * @param shown       the pull request dialog
 * @param comments    the conversation as it was read, or why that failed; nothing while it is first read
 * @param highlighted the comment the user moved to; none for the newest, which the popup opens on
 */
@With
public record CommentsPopup(
        PullRequestPopup shown,
        Option<Either<Failure, List<PullRequestComment>>> comments,
        Option<PullRequestComment.Id> highlighted)
        implements ShowsPullRequest {

    /**
     * The popup while the comments are read, with nothing highlighted.
     */
    public static CommentsPopup loading(PullRequestPopup shown) {
        return new CommentsPopup(shown, Option.none(), Option.none());
    }

    /**
     * The popup with the comments as they were read again; the highlight stays on its comment, if it is still there.
     */
    public CommentsPopup read(Either<Failure, List<PullRequestComment>> read) {
        return new CommentsPopup(
                shown,
                Option.of(read),
                highlighted.filter(id ->
                        read.exists(all -> all.exists(comment -> comment.id().equals(id)))));
    }

    /**
     * The comments, once read; none while they are read or when that failed.
     */
    public List<PullRequestComment> all() {
        return comments.flatMap(Either::toOption).getOrElse(List.empty());
    }

    /**
     * Where the highlighted comment is among {@link #all()}: the one moved to, else the newest. Only empty when there
     * are no comments.
     */
    public Option<Integer> highlightedIndex() {
        List<PullRequestComment> all = all();
        return highlighted
                .map(id -> all.indexWhere(comment -> comment.id().equals(id)))
                .filter(index -> index >= 0)
                .orElse(() -> Option.when(!all.isEmpty(), all.size() - 1));
    }

    public Option<PullRequestComment> highlightedComment() {
        return highlightedIndex().map(all()::get);
    }

    @Override
    public PullRequestPopup pullRequestPopup() {
        return shown;
    }

    @Override
    public CommentsPopup withPullRequestPopup(PullRequestPopup dialog) {
        return withShown(dialog);
    }
}
