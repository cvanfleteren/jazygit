package net.vanfleteren.jazygit.feature.worktree.add;

import net.vanfleteren.jazygit.state.TextPopup;

/**
 * Asking for the name of a new branch to check out in a new worktree, which starts where {@code from} says, shown
 * to the user as {@code label}.
 *
 * @param name the name as far as it was typed; it starts out empty, or as the remote branch is named
 */
public record NewWorktreeBranchPopup(String label, String name, NewBranch from) implements TextPopup {

    @Override
    public String text() {
        return name;
    }

    @Override
    public NewWorktreeBranchPopup withText(String text) {
        return new NewWorktreeBranchPopup(label, text, from);
    }
}
