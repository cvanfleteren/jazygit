package net.vanfleteren.jazygit.feature.worktree.remove;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.worktree.remove.RemoveWorktreeMsg.*;
import net.vanfleteren.jazygit.git.model.Worktree;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Removing a worktree, and deleting its branch with it when the user chose so; when it has changes, removing it
 * anyway is offered.
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
                                        model,
                                        new RemoveWorktreePopup(
                                                worktree.location(), worktree.name(), worktree.branch()))))
                        .getOrElse(() -> Next.of(model));
            case Chosen(boolean deleteBranch) ->
                Update.confirm(
                        model,
                        RemoveWorktreePopup.class,
                        popup -> model.repo()
                                .worktree(popup.location())
                                .filter(worktree -> notRemovable(worktree).isEmpty())
                                .filter(worktree -> !deleteBranch || branchDeletable(model, popup))
                                .map(worktree -> new RemoveWorktreeCmd(
                                        popup.location(), false, popup.branch().filter(branch -> deleteBranch))));
            // The removal ran, and failed; removing it anyway is offered instead of the failure.
            case NeedsForce(var cmd, var commands) ->
                Update.refresh(OperationUpdate.done(model, cmd, commands)
                        .openPopupIfFree(new ForceRemoveWorktreePopup(
                                cmd,
                                model.repo()
                                        .worktree(cmd.location())
                                        .map(Worktree::name)
                                        .getOrElse(cmd.location()))));
        };
    }

    /**
     * Why {@code worktree} cannot be removed, if it cannot: it is the main one, where the repository lives, the one
     * shown, or locked, which git refuses.
     */
    public static Option<AppError> notRemovable(Worktree worktree) {
        return worktree.main()
                ? Option.of(AppError.refused("error.removeWorktree.main", worktree.name()))
                : worktree.current()
                        ? Option.of(AppError.refused("error.removeWorktree.shown", worktree.name()))
                        : worktree.locked().map(reason -> new AppError.WorktreeLocked(worktree.name(), reason));
    }

    /**
     * Whether the branch of the worktree {@code popup} asks about can be deleted with it: it has one, and no
     * operation runs on it.
     */
    static boolean branchDeletable(Model model, RemoveWorktreePopup popup) {
        return popup.branch().flatMap(model::idleBranch).isDefined();
    }
}
