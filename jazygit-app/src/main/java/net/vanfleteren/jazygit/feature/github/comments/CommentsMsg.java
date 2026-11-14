package net.vanfleteren.jazygit.feature.github.comments;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.model.PullRequestComment;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

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
