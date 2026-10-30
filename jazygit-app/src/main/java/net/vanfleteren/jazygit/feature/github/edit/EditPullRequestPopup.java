package net.vanfleteren.jazygit.feature.github.edit;

import io.vavr.collection.List;
import lombok.With;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.feature.github.show.ShowsPullRequest;
import net.vanfleteren.jazygit.git.model.RemoteBranch;

/**
 * Editing the pull request that {@code from} shows (ADR 59). Only one popup is open at a time, so this one keeps the
 * pull request dialog it was opened from, to go back to.
 *
 * @param from            the pull request dialog it was opened from, as it is now
 * @param bases           the branches it can merge into, the one it merges into now first
 * @param opened          what the pull request said when the popup opened, or as it was saved since
 * @param fields          what it says as far as the user typed, picked and ticked it
 * @param baseChangeable  whether its base can be changed: not of one stacked on another, nor of a closed or merged one
 * @param draftChangeable whether it can be made a draft or ready for review: not a closed or merged one
 * @param saving          whether it is being saved; it can then not be changed, saved again or left
 */
@With
public record EditPullRequestPopup(
        PullRequestPopup from,
        List<RemoteBranch> bases,
        PullRequestFields opened,
        PullRequestFields fields,
        boolean baseChangeable,
        boolean draftChangeable,
        boolean saving)
        implements ShowsPullRequest {

    @Override
    public PullRequestPopup pullRequestPopup() {
        return from;
    }

    @Override
    public EditPullRequestPopup withPullRequestPopup(PullRequestPopup dialog) {
        return withFrom(dialog);
    }
}
