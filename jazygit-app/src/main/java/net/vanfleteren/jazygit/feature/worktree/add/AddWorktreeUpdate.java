package net.vanfleteren.jazygit.feature.worktree.add;

import io.vavr.collection.List;
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.repos.RecentReposCmd;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeMsg.*;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Worktree;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Adding a worktree for a branch, an existing one or a new one made from a branch or following a remote one.
 */
public final class AddWorktreeUpdate {

    private AddWorktreeUpdate() {}

    public static Next update(Model model, AddWorktreeMsg msg) {
        return switch (msg) {
            case Requested(Branch.Name branch) -> requested(model, branch);
            case NewBranchRequested(Branch.Name base) ->
                Update.ask(
                        model,
                        model.idleBranch(base)
                                .map(known -> new NewWorktreeBranchPopup(
                                        base.value(), "", new NewBranch(base.value(), Option.of(base), false))));
            // It starts out named as the remote branch, which it follows.
            case NewBranchAtRemoteRequested(RemoteBranch remote) ->
                Update.ask(
                        model,
                        model.repo()
                                .remoteBranch(remote)
                                .map(known -> new NewWorktreeBranchPopup(
                                        remote.shortName(),
                                        remote.branch().value(),
                                        new NewBranch(remote.trackingRef(), Option.none(), true))));
            case NameCancelled() -> Next.of(model.withoutPopup(NewWorktreeBranchPopup.class));
            case NameConfirmed() -> nameConfirmed(model);
            case Cancelled() -> Next.of(model.withoutPopup(NewWorktreePopup.class));
            // A blank location keeps the dialog open.
            case Confirmed() ->
                Update.confirm(
                        model,
                        NewWorktreePopup.class,
                        popup -> Option.of(popup.location().strip())
                                .filter(text -> !text.isEmpty())
                                .map(text -> new AddWorktreeCmd(popup.branch(), text, popup.creating())));
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
     * {@link #location}. The branch checked out here git keeps from being checked out twice, so a new branch made
     * from it is asked for instead; one that another worktree has checked out git would refuse too, which is said.
     */
    private static Next requested(Model model, Branch.Name branch) {
        return model.idleBranch(branch)
                .map(known -> known.current()
                        ? update(model, new NewBranchRequested(branch))
                        : known.worktreeLocation()
                                .map(worktree -> Next.of(model.fail(new AppError.CheckedOutElsewhere(
                                        AddWorktreeCmd.ADD_WORKTREE, branch, worktree))))
                                .getOrElse(
                                        () -> Update.ask(model, new NewWorktreePopup(branch, location(model, branch)))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Asks where the worktree of the new branch, named as typed, goes. A name that is no branch name, like a blank
     * one, keeps the dialog open.
     */
    private static Next nameConfirmed(Model model) {
        return model.popup(NewWorktreeBranchPopup.class)
                .flatMap(popup -> Branch.Name.parse(popup.name().strip())
                        .map(name -> Update.ask(
                                model.withoutPopup(NewWorktreeBranchPopup.class),
                                new NewWorktreePopup(name, location(model, name), Option.of(popup.from())))))
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
