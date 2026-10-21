package net.vanfleteren.jazygit.feature.worktree.add;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.TextPopup;

/**
 * Asking where the new worktree for {@code branch} goes.
 *
 * @param location where it goes, as far as it was typed; it starts out next to the main worktree
 * @param creating the branch to create along with it, when {@code branch} is a new one
 */
public record NewWorktreePopup(Branch.Name branch, String location, Option<NewBranch> creating) implements TextPopup {

    /**
     * Asking where the new worktree for {@code branch}, which exists, goes.
     */
    public NewWorktreePopup(Branch.Name branch, String location) {
        this(branch, location, Option.none());
    }

    @Override
    public String text() {
        return location;
    }

    @Override
    public NewWorktreePopup withText(String text) {
        return new NewWorktreePopup(branch, text, creating);
    }
}
