package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.collection.List;
import lombok.With;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking what the new pull request of {@code branch} on Forgejo says (ADR 178): which of {@code bases} it merges into,
 * its title, its body, and whether it is a draft.
 *
 * @param bases     the branches of the home's remote to choose from, the parent first
 * @param base      the one picked: the highlighted one, or the one highlighted last while the filter shows none
 * @param titleRead the title read last, for a base: the subject of the oldest commit not on it
 * @param title     the title as far as the user typed it; it starts out as {@code titleRead}
 * @param body      what it says, which starts out empty
 * @param draft     whether it is a draft
 */
@With
public record NewPullRequestPopup(
        BranchName branch,
        List<BranchName> bases,
        BranchName base,
        String titleRead,
        String title,
        String body,
        boolean draft)
        implements Popup {

    /**
     * The popup as it opens, with {@code title} read for the first of {@code bases}.
     */
    static NewPullRequestPopup opening(BranchName branch, List<BranchName> bases, String title) {
        return new NewPullRequestPopup(branch, bases, bases.head(), title, title, "", false);
    }

    /**
     * Whether the title is still the one read, which reading it for another base then replaces.
     */
    boolean titleUntouched() {
        return title.equals(titleRead);
    }
}
