package net.vanfleteren.jazygit.feature.github.create;

import io.vavr.collection.List;
import lombok.With;
import net.vanfleteren.jazygit.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking what the new pull request of {@code branch} says: which of {@code bases} it merges into, its title, its
 * body, and whether it is a draft.
 *
 * @param head        the branch on its remote that it pushes to, which gh needs
 * @param bases       the branches to choose from, the parent first
 * @param base        the one picked: the highlighted one, or the one highlighted last while the filter shows none
 * @param titleRead   the title read last, for a base: the subject of the oldest commit not on it
 * @param pullRequest what it says as far as the user typed and ticked it; the title starts out as {@code titleRead}
 */
@With
public record NewPullRequestPopup(
        BranchName branch,
        RemoteBranch head,
        List<BranchName> bases,
        BranchName base,
        String titleRead,
        NewPullRequest pullRequest)
        implements Popup {

    /**
     * Whether the title is still the one read, which reading it for another base then replaces.
     */
    boolean titleUntouched() {
        return pullRequest.title().equals(titleRead);
    }
}
