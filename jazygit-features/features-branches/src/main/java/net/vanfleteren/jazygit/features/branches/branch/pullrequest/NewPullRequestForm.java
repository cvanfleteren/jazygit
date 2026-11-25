package net.vanfleteren.jazygit.features.branches.branch.pullrequest;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * The popup of a forge that asks what a new pull request merges into and what it says (ADR 18, 175), as far as its
 * title follows the base picked: the title starts out as the subject of the oldest commit not on the base, and is read
 * again when another base is picked, until the user types one. Each forge's popup is its own; this is what they have
 * in common.
 */
public interface NewPullRequestForm extends Popup {

    /**
     * The branch the pull request is created for.
     */
    BranchName branch();

    /**
     * The base picked: the highlighted one, or the one highlighted last while the filter shows none.
     */
    BranchName base();

    /**
     * The title read last, for a base.
     */
    String titleRead();

    /**
     * The title as far as the user typed it.
     */
    String title();

    /**
     * This popup with {@code base} picked.
     */
    NewPullRequestForm withBase(BranchName base);

    /**
     * This popup with {@code title} read for its base, as the title too.
     */
    NewPullRequestForm titled(String title);

    /**
     * The branch {@code base} is on its remote, which the title is read against; none when that cannot be told.
     */
    Option<BranchRef> baseRef(Model model, BranchName base);

    /**
     * Whether the title is still the one read, which reading it for another base then replaces.
     */
    default boolean titleUntouched() {
        return title().equals(titleRead());
    }
}
