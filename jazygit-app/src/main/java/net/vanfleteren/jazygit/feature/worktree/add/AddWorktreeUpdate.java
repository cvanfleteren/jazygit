package net.vanfleteren.jazygit.feature.worktree.add;

import io.vavr.collection.List;
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.repos.RecentReposCmd;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeMsg.*;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
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
            case Requested(BranchName branch) -> requested(model, branch);
            case NewBranchRequested(BranchName base) ->
                Update.ask(
                        model,
                        model.idleBranch(base).map(known -> NewWorktreeBranchPopup.fromLocal(base, Option.none())));
            // It starts out named as the remote branch, which it follows.
            case NewBranchAtRemoteRequested(RemoteBranch remote) ->
                Update.ask(
                        model,
                        model.repo()
                                .remoteBranch(remote)
                                .map(known -> NewWorktreeBranchPopup.followingRemote(remote, Option.none())));
            // It starts out at the branch checked out here, or on no branch at the commit checked out.
            case StartRequested() ->
                Update.ask(
                        model,
                        new NewWorktreeStartPopup(model.repo()
                                .currentBranch()
                                .map(current -> current.name().value())
                                .getOrElse("HEAD")));
            case StartConfirmed() -> startConfirmed(model);
            // What was typed starts the worktree once git said which commit it names (ADR 40).
            case StartResolved(String typed, var commit) ->
                commit.map(sha -> Update.ask(model, NewWorktreeBranchPopup.fromCommit(typed, sha)))
                        .getOrElse(() -> Next.of(model.fail(new AppError.UnknownRevision(typed))));
            case StartNotResolved(String typed, var failure) ->
                Next.of(model.fail(new AppError.OperationFailed(
                        AddWorktreeCmd.ADD_WORKTREE, io.vavr.control.Option.of(typed), failure)));
            case NameConfirmed() -> nameConfirmed(model);
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
    private static Next requested(Model model, BranchName branch) {
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
                    Model asked = model.withoutPopup(NewWorktreeStartPopup.class);
                    return switch (resolve(model, start)) {
                        case Start.Local(BranchName branch) ->
                            Update.ask(
                                    asked,
                                    NewWorktreeBranchPopup.fromLocal(
                                            branch,
                                            Option.of(model.idleBranch(branch)
                                                    .filter(known -> !known.current() && !known.checkedOutElsewhere())
                                                    .<WorktreeSource>map(known -> new WorktreeSource.Existing(branch))
                                                    .getOrElse(() -> new WorktreeSource.Detached(
                                                            new BranchRef.Local(branch), start)))));
                        case Start.Remote(RemoteBranch followed) ->
                            Update.ask(
                                    asked,
                                    NewWorktreeBranchPopup.followingRemote(
                                            followed,
                                            Option.of(new WorktreeSource.Detached(followed.tracking(), start))));
                        // Anything else is asked of git first.
                        case Start.Revision(String revision) -> Next.of(asked, new ResolveStartCmd(revision));
                    };
                })
                .getOrElse(() -> Next.of(model));
    }

    /**
     * What a typed start names: a local branch, else a remote branch by its short name, else any other revision.
     */
    private static Start resolve(Model model, String start) {
        return model.repo()
                .branch(new BranchName(start))
                .<Start>map(local -> new Start.Local(local.name()))
                .orElse(() -> model.repo()
                        .remoteBranches()
                        .toOption()
                        .flatMap(known -> known.map(RemoteRef::branch)
                                .find(branch -> branch.shortName().equals(start)))
                        .map(Start.Remote::new))
                .getOrElse(() -> new Start.Revision(start));
    }

    /**
     * Asks where the worktree of the new branch, named as typed, goes. A blank name goes on without a new branch, when
     * the popup allows it; a name that is no branch name, like a blank one otherwise, keeps the dialog open.
     */
    private static Next nameConfirmed(Model model) {
        return model.popup(NewWorktreeBranchPopup.class)
                .flatMap(popup -> popup.name().isBlank()
                        ? popup.ifBlank()
                        : BranchName.parse(popup.name().strip())
                                .map(name -> new WorktreeSource.Created(name, popup.from())))
                .map(source -> ask(model.withoutPopup(NewWorktreeBranchPopup.class), source))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Where a new worktree named after {@code name} starts out: next to the main worktree, named after it and
     * {@code name}; without one, as next to a bare repository, next to the one shown, named after {@code name}. A
     * location a worktree has already gets a number appended. Empty while the worktrees are not read.
     */
    static String location(Model model, BranchName name) {
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

    /**
     * What a typed start names, as {@link #resolve} works it out.
     */
    private sealed interface Start {

        record Local(BranchName branch) implements Start {}

        record Remote(RemoteBranch branch) implements Start {}

        record Revision(String start) implements Start {}
    }
}
