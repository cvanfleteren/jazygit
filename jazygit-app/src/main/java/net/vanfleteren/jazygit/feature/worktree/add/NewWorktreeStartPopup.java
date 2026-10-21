package net.vanfleteren.jazygit.feature.worktree.add;

import net.vanfleteren.jazygit.state.TextPopup;

/**
 * Asking where a new worktree starts: a branch, local or remote, a commit or any other revision.
 *
 * @param start the start as far as it was typed; it starts out as the branch checked out here
 */
public record NewWorktreeStartPopup(String start) implements TextPopup {

    @Override
    public String text() {
        return start;
    }

    @Override
    public NewWorktreeStartPopup withText(String text) {
        return new NewWorktreeStartPopup(text);
    }
}
