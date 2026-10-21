package net.vanfleteren.jazygit.feature.worktree.add;

import net.vanfleteren.jazygit.state.TextPopup;

/**
 * Asking where the new worktree that checks out {@code source} goes.
 *
 * @param location where it goes, as far as it was typed; it starts out next to the main worktree
 */
public record NewWorktreePopup(WorktreeSource source, String location) implements TextPopup {

    @Override
    public String text() {
        return location;
    }

    @Override
    public NewWorktreePopup withText(String text) {
        return new NewWorktreePopup(source, text);
    }
}
