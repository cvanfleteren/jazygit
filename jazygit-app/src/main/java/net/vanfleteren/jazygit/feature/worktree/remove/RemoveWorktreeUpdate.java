package net.vanfleteren.jazygit.feature.worktree.remove;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.worktree.WorktreeCmd;
import net.vanfleteren.jazygit.feature.worktree.remove.RemoveWorktreeMsg.*;
import net.vanfleteren.jazygit.git.model.Worktree;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Removing a worktree; when it has changes, removing it anyway is offered.
 */
public final class RemoveWorktreeUpdate {

    private RemoveWorktreeUpdate() {}

    public static Next update(Model model, RemoveWorktreeMsg msg) {
        return switch (msg) {
            case Requested(String location) ->
                Update.ask(
                        model,
                        model.repo()
                                .worktree(location)
                                .filter(RemoveWorktreeUpdate::removable)
                                .map(worktree -> new RemoveWorktreePopup(worktree.location(), worktree.name())));
            case Cancelled() -> Next.of(model.withoutPopup(RemoveWorktreePopup.class));
            case Confirmed() ->
                Update.confirm(
                        model,
                        RemoveWorktreePopup.class,
                        popup -> model.repo()
                                .worktree(popup.location())
                                .filter(RemoveWorktreeUpdate::removable)
                                .map(worktree -> new WorktreeCmd.Remove(popup.location(), false)));
            // The removal ran, and failed; removing it anyway is offered instead of the failure.
            case NeedsForce(var cmd, var commands) ->
                Update.refresh(OperationUpdate.done(model, cmd, commands)
                        .openPopupIfFree(new ForceRemoveWorktreePopup(
                                cmd.location(),
                                model.repo()
                                        .worktree(cmd.location())
                                        .map(Worktree::name)
                                        .getOrElse(cmd.location()))));
            case ForceCancelled() -> Next.of(model.withoutPopup(ForceRemoveWorktreePopup.class));
            case ForceConfirmed() ->
                Update.confirm(
                        model,
                        ForceRemoveWorktreePopup.class,
                        popup -> Option.of(new WorktreeCmd.Remove(popup.location(), true)));
        };
    }

    /**
     * Whether {@code worktree} can be removed: neither the main one, where the repository lives, nor the one shown.
     */
    private static boolean removable(Worktree worktree) {
        return !worktree.main() && !worktree.current();
    }
}
