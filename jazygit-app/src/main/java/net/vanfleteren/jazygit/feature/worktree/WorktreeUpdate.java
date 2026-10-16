package net.vanfleteren.jazygit.feature.worktree;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.repos.RecentReposCmd;
import net.vanfleteren.jazygit.feature.worktree.WorktreeMsg.*;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Worktree;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Adding a worktree for a branch, removing one, and switching to one, which the app then shows as if it had been
 * started there.
 */
public final class WorktreeUpdate {

    private WorktreeUpdate() {}

    public static Next update(Model model, WorktreeMsg msg) {
        return switch (msg) {
            case NewRequested(Branch.Name branch) -> newRequested(model, branch);
            case NewCancelled() -> Next.of(model.withoutPopup(NewWorktreePopup.class));
            // A blank location keeps the dialog open.
            case NewConfirmed(String location) ->
                Update.confirm(
                        model,
                        NewWorktreePopup.class,
                        popup -> Option.of(location.strip())
                                .filter(text -> !text.isEmpty())
                                .map(text -> new WorktreeCmd.Add(popup.branch(), text)));
            // The worktrees are shown, the new one among them, to switch to.
            case Added(var cmd, var commands) ->
                OperationUpdate.succeeded(model.withBranchesTab(BranchesTab.WORKTREES), cmd, commands);
            case SwitchRequested(String location) -> switchRequested(model, location);
            case RemoveRequested(String location) ->
                Update.ask(
                        model,
                        model.worktree(location)
                                .filter(WorktreeUpdate::removable)
                                .map(worktree -> new RemoveWorktreePopup(worktree.location(), worktree.name())));
            case RemoveCancelled() -> Next.of(model.withoutPopup(RemoveWorktreePopup.class));
            case RemoveConfirmed() ->
                Update.confirm(
                        model,
                        RemoveWorktreePopup.class,
                        popup -> model.worktree(popup.location())
                                .filter(WorktreeUpdate::removable)
                                .map(worktree -> new WorktreeCmd.Remove(popup.location(), false)));
            // The removal ran, and failed; removing it anyway is offered instead of the failure.
            case NeedsForce(var cmd, var commands) ->
                Update.refresh(OperationUpdate.done(model, cmd, commands)
                        .openPopupIfFree(new ForceRemoveWorktreePopup(
                                cmd.location(),
                                model.worktree(cmd.location())
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
     * Asks where to check out {@code branch}, a known branch that is not checked out and that no operation runs
     * on, starting out next to the main worktree. One that another worktree has checked out git would refuse, so
     * that is said instead.
     */
    private static Next newRequested(Model model, Branch.Name branch) {
        return model.idleBranch(branch)
                .filter(known -> !known.current())
                .map(known -> known.worktreeLocation()
                        .map(worktree -> Next.of(
                                model.fail(new AppError.CheckedOutElsewhere(Operation.ADD_WORKTREE, branch, worktree))))
                        .getOrElse(() -> Update.ask(
                                model,
                                new NewWorktreePopup(
                                        branch,
                                        model.mainWorktree()
                                                .map(main -> main.siblingFor(branch))
                                                .getOrElse("")))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Opens the worktree at {@code location} in place of this one, through the repositories the app knows; the
     * one shown already stays.
     */
    private static Next switchRequested(Model model, String location) {
        return model.worktree(location)
                .filter(worktree -> !worktree.current())
                .map(worktree -> Next.of(model.clearError(), new RecentReposCmd.SwitchTo(location)))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Whether {@code worktree} can be removed: neither the main one, where the repository lives, nor the one shown.
     */
    private static boolean removable(Worktree worktree) {
        return !worktree.main() && !worktree.current();
    }
}
