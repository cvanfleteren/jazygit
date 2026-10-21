package net.vanfleteren.jazygit.feature.worktree.add;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.TextPopup;

/**
 * Asking for the name of a new branch to check out in a new worktree, which starts where {@code from} says, shown
 * to the user as {@code label}.
 *
 * @param name    the name as far as it was typed; it starts out empty, or as the remote branch is named
 * @param ifBlank what the worktree checks out when the name is left blank; without it, a blank name is no name
 */
public record NewWorktreeBranchPopup(String label, String name, NewBranch from, Option<WorktreeSource> ifBlank)
        implements TextPopup {

    /**
     * Asking for the name of a new branch, which has to be given.
     */
    public NewWorktreeBranchPopup(String label, String name, NewBranch from) {
        this(label, name, from, Option.none());
    }

    @Override
    public String text() {
        return name;
    }

    @Override
    public NewWorktreeBranchPopup withText(String text) {
        return new NewWorktreeBranchPopup(label, text, from, ifBlank);
    }
}
