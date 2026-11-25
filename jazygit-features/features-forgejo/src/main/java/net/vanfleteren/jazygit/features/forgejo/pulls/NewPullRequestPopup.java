package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.FormOpening;
import net.vanfleteren.jazygit.features.branches.branch.pullrequest.NewPullRequestForm;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.forge.ForgeHome;

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
        implements NewPullRequestForm {

    /**
     * The popup as it opens, with {@code title} read for the first of {@code bases}.
     */
    static NewPullRequestPopup opening(BranchName branch, List<BranchName> bases, String title) {
        return new NewPullRequestPopup(branch, bases, bases.head(), title, title, "", false);
    }

    @Override
    public NewPullRequestPopup titled(String title) {
        return withTitleRead(title).withTitle(title);
    }

    /**
     * The branch {@code base} is on the home's remote; none without a home.
     */
    @Override
    public Option<BranchRef> baseRef(Model model, BranchName base) {
        return ForgeHome.of(model).map(home -> CreatePullRequestUpdate.baseRef(home, base));
    }

    /**
     * Opening the popup for {@code branch} into the first of {@code bases}, once its title is read.
     */
    public record Opening(BranchName branch, List<BranchName> bases) implements FormOpening {

        @Override
        public NewPullRequestPopup titled(String title) {
            return opening(branch, bases, title);
        }
    }
}
