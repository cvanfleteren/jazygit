package net.vanfleteren.jazygit.feature.worktree.remove;

import io.vavr.control.Option;
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
                model.repo()
                        .worktree(location)
                        .map(worktree -> notRemovable(worktree)
                                .map(error -> Next.of(model.fail(error)))
                                .getOrElse(() -> Update.ask(
                                        model, new RemoveWorktreePopup(worktree.location(), worktree.name()))))
                        .getOrElse(() -> Next.of(model));
            case Cancelled() -> Next.of(model.withoutPopup(RemoveWorktreePopup.class));
            case Confirmed() ->
                Update.confirm(
                        model,
                        RemoveWorktreePopup.class,
                        popup -> model.repo()
                                .worktree(popup.location())
                                .filter(worktree -> notRemovable(worktree).isEmpty())
                                .map(worktree -> new RemoveWorktreeCmd(popup.location(), false)));
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
                        popup -> Option.of(new RemoveWorktreeCmd(popup.location(), true)));
        };
    }

    /**
     * Why {@code worktree} cannot be removed, if it cannot: it is the main one, where the repository lives, the one
     * shown, or locked, which git refuses.
     */
    private static Option<AppError> notRemovable(Worktree worktree) {
        return worktree.main()
                ? Option.of(new AppError.MainWorktreeNotRemovable(worktree.name()))
                : worktree.current()
                        ? Option.of(new AppError.ShownWorktreeNotRemovable(worktree.name()))
                        : Option.when(worktree.locked(), () -> new AppError.WorktreeLocked(worktree.name()));
    }
}
