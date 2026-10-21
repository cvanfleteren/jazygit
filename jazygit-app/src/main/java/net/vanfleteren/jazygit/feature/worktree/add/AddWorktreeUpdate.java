package net.vanfleteren.jazygit.feature.worktree.add;

import io.vavr.collection.List;
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.repos.RecentReposCmd;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeMsg.*;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteRef;
import net.vanfleteren.jazygit.git.model.Worktree;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Adding a worktree: for a branch, an existing one or a new one made from a branch or following a remote one, for
 * any revision, or for a pull request.
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
            // It starts out at the branch checked out here, or on no branch at the commit checked out.
            case StartRequested() ->
                Update.ask(
                        model,
                        new NewWorktreeStartPopup(model.repo()
                                .currentBranch()
                                .map(current -> current.name().value())
                                .getOrElse("HEAD")));
            case StartCancelled() -> Next.of(model.withoutPopup(NewWorktreeStartPopup.class));
            case StartConfirmed() -> startConfirmed(model);
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
                                .map(text -> new AddWorktreeCmd(popup.source(), text)));
            // The worktrees are shown, the new one among them, and switching to it is offered.
            case Added(var cmd, String location, var commands) -> {
                Next succeeded = OperationUpdate.succeeded(
                        model.mapRepoView(repoView -> repoView.withBranchesTab(BranchesTab.WORKTREES)), cmd, commands);
                yield new Next(
                        succeeded
                                .model()
                                .openPopupIfFree(
                                        new SwitchToAddedPopup(cmd.source().label(), location)),
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
     * Asks where the new worktree that checks out {@code source} goes, starting out at {@link #location}.
     */
    public static Next ask(Model model, WorktreeSource source) {
        return Update.ask(model, new NewWorktreePopup(source, location(model, source.directory())));
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
                                .getOrElse(() -> ask(model, new WorktreeSource.Existing(branch))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Asks for the name of a new branch starting where was typed, which may be left blank: the worktree then checks
     * out the local branch typed, when it can, or else what was typed on no branch. A new branch made from a local
     * branch records it as its parent; one made from a remote branch follows it, and starts out named as it. A blank
     * start keeps the dialog open, and so does one starting with {@code -}, which git would take for an option.
     */
    private static Next startConfirmed(Model model) {
        return model.popup(NewWorktreeStartPopup.class)
                .map(popup -> popup.start().strip())
                .filter(start -> !start.isEmpty() && !start.startsWith("-"))
                .map(start -> {
                    Option<Branch> local = model.repo().branch(new Branch.Name(start));
                    Option<RemoteBranch> remote = local.isDefined()
                            ? Option.none()
                            : model.repo()
                                    .remoteBranches()
                                    .toOption()
                                    .flatMap(known -> known.map(RemoteRef::branch)
                                            .find(branch -> branch.shortName().equals(start)));
                    NewBranch from = remote.map(followed -> new NewBranch(followed.trackingRef(), Option.none(), true))
                            .getOrElse(() -> new NewBranch(start, local.map(Branch::name), false));
                    WorktreeSource ifBlank = local.filter(branch -> !branch.current() && !branch.checkedOutElsewhere())
                            .flatMap(branch -> model.idleBranch(branch.name()))
                            .<WorktreeSource>map(branch -> new WorktreeSource.Existing(branch.name()))
                            .getOrElse(() -> new WorktreeSource.Detached(start));
                    return Update.ask(
                            model.withoutPopup(NewWorktreeStartPopup.class),
                            new NewWorktreeBranchPopup(
                                    start,
                                    remote.map(followed -> followed.branch().value())
                                            .getOrElse(""),
                                    from,
                                    Option.of(ifBlank)));
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Asks where the worktree of the new branch, named as typed, goes. A blank name goes on without a new branch, when
     * the popup allows it; a name that is no branch name, like a blank one otherwise, keeps the dialog open.
     */
    private static Next nameConfirmed(Model model) {
        return model.popup(NewWorktreeBranchPopup.class)
                .flatMap(popup -> popup.name().isBlank()
                        ? popup.ifBlank()
                        : Branch.Name.parse(popup.name().strip())
                                .map(name -> new WorktreeSource.Created(name, popup.from())))
                .map(source -> ask(model.withoutPopup(NewWorktreeBranchPopup.class), source))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Where a new worktree named after {@code name} starts out: next to the main worktree, named after it and
     * {@code name}; without one, as next to a bare repository, next to the one shown, named after {@code name}. A
     * location a worktree has already gets a number appended. Empty while the worktrees are not read.
     */
    static String location(Model model, Branch.Name name) {
        List<String> taken =
                model.repo().worktrees().toOption().getOrElse(List.empty()).map(Worktree::location);
        return model.repo()
                .mainWorktree()
                .map(main -> main.siblingFor(name))
                .orElse(() -> model.repo().currentWorktree().map(current -> current.nextTo(name)))
                .map(wanted -> Stream.from(1)
                        .map(n -> n == 1 ? wanted : wanted + "-" + n)
                        .find(candidate -> !taken.contains(candidate))
                        .get())
                .getOrElse("");
    }
}
