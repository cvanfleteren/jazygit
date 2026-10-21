package net.vanfleteren.jazygit.feature.worktree.add;

import io.vavr.collection.List;
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.repos.RecentReposCmd;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeMsg.*;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Worktree;
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
     * Asks where to check out {@code branch}, a known branch that no operation runs on, starting out at
     * {@link #location}. One that this worktree or another one has checked out git would refuse, so that is said
     * instead.
     */
    private static Next requested(Model model, Branch.Name branch) {
        return model.idleBranch(branch)
                .map(known -> known.current()
                        ? Next.of(model.fail(new AppError.CheckedOutHere(branch)))
                        : known.worktreeLocation()
                                .map(worktree -> Next.of(model.fail(new AppError.CheckedOutElsewhere(
                                        AddWorktreeCmd.ADD_WORKTREE, branch, worktree))))
                                .getOrElse(
                                        () -> Update.ask(model, new NewWorktreePopup(branch, location(model, branch)))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Where a new worktree for {@code branch} starts out: next to the main worktree, named after it and the branch;
     * without one, as next to a bare repository, next to the one shown, named after the branch. A location a
     * worktree has already gets a number appended. Empty while the worktrees are not read.
     */
    static String location(Model model, Branch.Name branch) {
        List<String> taken =
                model.repo().worktrees().toOption().getOrElse(List.empty()).map(Worktree::location);
        return model.repo()
                .mainWorktree()
                .map(main -> main.siblingFor(branch))
                .orElse(() -> model.repo().currentWorktree().map(current -> current.nextTo(branch)))
                .map(wanted -> Stream.from(1)
                        .map(n -> n == 1 ? wanted : wanted + "-" + n)
                        .find(candidate -> !taken.contains(candidate))
                        .get())
                .getOrElse("");
    }
}
