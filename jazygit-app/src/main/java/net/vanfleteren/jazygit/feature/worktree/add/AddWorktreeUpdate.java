package net.vanfleteren.jazygit.feature.worktree.add;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.repos.RecentReposCmd;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeMsg.*;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Adding a worktree for a branch.
 */
public final class AddWorktreeUpdate {

    private AddWorktreeUpdate() {}

    public static Next update(Model model, AddWorktreeMsg msg) {
        return switch (msg) {
            case Requested(Branch.Name branch) -> requested(model, branch);
            case Cancelled() -> Next.of(model.withoutPopup(NewWorktreePopup.class));
            // A blank location keeps the dialog open.
            case Confirmed() ->
                Update.confirm(
                        model,
                        NewWorktreePopup.class,
                        popup -> Option.of(popup.location().strip())
                                .filter(text -> !text.isEmpty())
                                .map(text -> new AddWorktreeCmd(popup.branch(), text)));
            // The worktrees are shown, the new one among them, and switching to it is offered.
            case Added(var cmd, String location, var commands) -> {
                Next succeeded = OperationUpdate.succeeded(
                        model.mapRepoView(repoView -> repoView.withBranchesTab(BranchesTab.WORKTREES)), cmd, commands);
                yield new Next(
                        succeeded.model().openPopupIfFree(new SwitchToAddedPopup(cmd.branch(), location)),
                        succeeded.cmds());
            }
            case SwitchCancelled() -> Next.of(model.withoutPopup(SwitchToAddedPopup.class));
            case SwitchConfirmed() ->
                model.popup(SwitchToAddedPopup.class)
                        .map(popup -> Next.of(
                                model.withoutPopup(SwitchToAddedPopup.class).clearError(),
                                RecentReposCmd.SwitchTo.worktree(popup.location())))
                        .getOrElse(() -> Next.of(model));
        };
    }

    /**
     * Asks where to check out {@code branch}, a known branch that no operation runs on, starting out next to the
     * main worktree. One that this worktree or another one has checked out git would refuse, so that is said
     * instead.
     */
    private static Next requested(Model model, Branch.Name branch) {
        return model.idleBranch(branch)
                .map(known -> known.current()
                        ? Next.of(model.fail(new AppError.CheckedOutHere(branch)))
                        : known.worktreeLocation()
                                .map(worktree -> Next.of(model.fail(new AppError.CheckedOutElsewhere(
                                        AddWorktreeCmd.ADD_WORKTREE, branch, worktree))))
                                .getOrElse(() -> Update.ask(
                                        model,
                                        new NewWorktreePopup(
                                                branch,
                                                model.repo()
                                                        .mainWorktree()
                                                        .map(main -> main.siblingFor(branch))
                                                        .getOrElse("")))))
                .getOrElse(() -> Next.of(model));
    }
}
