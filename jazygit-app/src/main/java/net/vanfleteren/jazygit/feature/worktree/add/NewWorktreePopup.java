package net.vanfleteren.jazygit.feature.worktree.add;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.ConfirmablePopup;
import net.vanfleteren.jazygit.state.TextPopup;

/**
 * Asking where the new worktree that checks out {@code source} goes.
 *
 * @param location where it goes, as far as it was typed; it starts out next to the main worktree
 */
public record NewWorktreePopup(WorktreeSource source, String location) implements TextPopup, ConfirmablePopup {

    @Override
    public String text() {
        return location;
    }

    @Override
    public NewWorktreePopup withText(String text) {
        return new NewWorktreePopup(source, text);
    }

    @Override
    public Option<AddWorktreeCmd> confirm() {
        // A blank location keeps the dialog open.
        return Option.of(location.strip())
                .filter(text -> !text.isEmpty())
                .map(text -> new AddWorktreeCmd(source, text));
    }
}
