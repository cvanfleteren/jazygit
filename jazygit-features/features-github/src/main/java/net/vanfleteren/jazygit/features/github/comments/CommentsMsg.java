package net.vanfleteren.jazygit.features.github.comments;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.PullRequestComment;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.lib.image.Picture;

/**
 * Reading the conversation on the pull request that is shown.
 */
public sealed interface CommentsMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return CommentsUpdate.update(model, this);
    }

    /**
     * The user asked to see the comments on the pull request that is shown.
     */
    record Requested() implements CommentsMsg {}

    /**
     * The conversation on pull request {@code number} was read.
     */
    record Loaded(PrNumber number, List<PullRequestComment> comments) implements CommentsMsg {}

    record LoadFailed(PrNumber number, Failure failure) implements CommentsMsg {}

    /**
     * The image at {@code url}, in the conversation on pull request {@code number}, was read: as the picture to show,
     * or none when it could not be read or shown (ADR 190).
     */
    record PictureLoaded(PrNumber number, String url, Option<Picture> picture) implements CommentsMsg {}

    /**
     * The user moved to comment {@code id}.
     */
    record Highlighted(PullRequestComment.Id id) implements CommentsMsg {}

    /**
     * The user asked to open the pull request in the browser.
     */
    record OpenChosen() implements CommentsMsg {}

    /**
     * The user clicked on a link to {@code url} in the comment (ADR 96).
     */
    record LinkChosen(String url) implements CommentsMsg {}

    /**
     * The user went back to the pull request dialog.
     */
    record Closed() implements CommentsMsg {}
}
