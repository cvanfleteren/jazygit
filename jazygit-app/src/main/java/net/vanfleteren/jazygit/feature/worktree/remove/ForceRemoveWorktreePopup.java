package net.vanfleteren.jazygit.feature.worktree.remove;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.ConfirmablePopup;

/**
 * Warning that the worktree {@code removal} removes has changes, and asking to remove it anyway, deleting its branch
 * too when the removal does.
 *
 * @param name the name of its directory
 */
public record ForceRemoveWorktreePopup(RemoveWorktreeCmd removal, String name) implements ConfirmablePopup {
    @Override
    public Option<RemoveWorktreeCmd> confirm() {
        return Option.of(removal.forced());
    }
}
