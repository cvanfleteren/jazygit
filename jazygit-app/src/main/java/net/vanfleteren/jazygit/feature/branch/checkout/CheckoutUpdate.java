package net.vanfleteren.jazygit.feature.branch.checkout;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.Branches;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutMsg.*;
import net.vanfleteren.jazygit.feature.branch.newbranch.NewBranchPopup;
import net.vanfleteren.jazygit.feature.repos.RecentReposCmd;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeMsg;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeUpdate;
import net.vanfleteren.jazygit.feature.worktree.add.CatchUp;
import net.vanfleteren.jazygit.feature.worktree.add.WorktreeSource;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.NewBranchStart;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Worktree;
import net.vanfleteren.jazygit.state.Landing;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.operation.OperationUpdate;
import net.vanfleteren.jazygit.state.view.BranchesTab;

/**
 * Checking out a branch.
 */
public final class CheckoutUpdate {

    private CheckoutUpdate() {}

    public static Next update(Model model, CheckoutMsg msg) {
        return switch (msg) {
            case Requested(BranchName branch) -> requested(model, branch, new CatchUp.AsIs());
            case RemoteRequested(RemoteBranch branch) -> remoteRequested(model, branch);
            case RemoteInNewWorktreeRequested(RemoteBranch branch) -> remoteInNewWorktree(model, branch);
            // The checkout ran, and failed; the stash it needs is offered instead of the failure.
            case NeedsStash(var cmd, var commands) ->
                Update.refresh(OperationUpdate.done(model, cmd, commands)
                        .openPopupIfFree(new StashCheckoutPopup(cmd.branch())));
            case CheckedOut(var cmd, var commands) ->
                OperationUpdate.succeeded(
                        model.mapRepoView(repoView -> repoView.withBranchesTab(BranchesTab.LOCAL)), cmd, commands);
            // It is shown as a checkout here shows it: on the local branches, the focus where it was.
            case SwitchWorktreeConfirmed() ->
                model.popup(SwitchWorktreePopup.class)
                        .map(popup -> Next.of(
                                model.withoutPopup(SwitchWorktreePopup.class).clearError(),
                                new RecentReposCmd.SwitchTo(
                                        popup.worktree(),
                                        new Landing(
                                                BranchesTab.LOCAL,
                                                model.repoView().focusedPane()))))
                        .getOrElse(() -> Next.of(model));
            case Placed(CheckoutRequest request) -> place(model, request);
            case HereChosen() ->
                model.popup(CheckoutWherePopup.class)
                        .filter(popup -> !popup.here().isLocked())
                        .map(popup -> popup.request()
                                .here(model.withoutPopup(CheckoutWherePopup.class)
                                        .clearError()))
                        .getOrElse(() -> Next.of(model));
            case MainChosen() -> mainChosen(model);
            case NewWorktreeChosen() ->
                model.popup(CheckoutWherePopup.class)
                        .map(popup -> popup.request().inNewWorktree(model.withoutPopup(CheckoutWherePopup.class)))
                        .getOrElse(() -> Next.of(model));
        };
    }

    /**
     * Switches to the main worktree, which checks out what was asked for once it is read (ADR 68). It is shown as a
     * checkout there shows it: on the local branches, the focus where it was.
     */
    private static Next mainChosen(Model model) {
        return model.popup(CheckoutWherePopup.class)
                .flatMap(popup -> popup.main().map(main -> {
                    Landing there = new Landing(
                                    BranchesTab.LOCAL, model.repoView().focusedPane())
                            .followedBy(popup.request().again());
                    return Next.of(
                            model.withoutPopup(CheckoutWherePopup.class).clearError(),
                            new RecentReposCmd.SwitchTo(main.location(), there));
                }))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Checks out {@code request} where the user says: in a linked worktree, which may be someone else's, they are
     * asked whether here, in the main worktree or in a new one; anywhere else it is checked out here (ADR 68). Until
     * the worktrees are read, it is not known which one this is, and it is checked out here.
     */
    public static Next place(Model model, CheckoutRequest request) {
        return model.repo()
                .currentWorktree()
                .filter(here -> !here.main())
                .map(here -> {
                    Option<Worktree> main = model.repo().mainWorktree();
                    return Update.ask(
                            model, new CheckoutWherePopup(request, here, main, main.flatMap(model.repo()::hasChanges)));
                })
                .getOrElse(() -> request.here(model.clearError()));
    }

    /**
     * Checks out the local branch that follows {@code remote}, as {@link net.vanfleteren.jazygit.state.Repo#trackingBranch}
     * picks it; in a new worktree, once caught up with {@code remote}. When the branch checked out here follows it,
     * does nothing. Without one, creates it under the same name; when a local branch that follows something else
     * already has that name, asks for another.
     */
    private static Next remoteRequested(Model model, RemoteBranch remote) {
        // A branch checked out here already follows it: that is what was asked for, and nothing moves (ADR 109).
        if (!isIdleRemote(model, remote) || model.repo().followers(remote).exists(Branch::current)) {
            return Next.of(model);
        }
        return model.repo()
                .trackingBranch(remote)
                .map(local -> requested(model, local.name(), CatchUp.of(local)))
                .getOrElse(() -> model.repo().branch(remote.branch()).isDefined()
                        ? Update.ask(model, NewBranchPopup.following(remote))
                        : place(model, new CheckoutRequest.Remote(remote.branch(), remote)));
    }

    /**
     * Checks out the local branch that follows {@code remote} in a new worktree, as {@link #remoteRequested} picks
     * it, without asking where (ADR 109). One that another worktree has checked out is offered to be switched to; the
     * one checked out here a worktree cannot check out twice, so a new branch following {@code remote} is asked for,
     * as when a local branch that follows something else has its name.
     */
    private static Next remoteInNewWorktree(Model model, RemoteBranch remote) {
        if (!isIdleRemote(model, remote)) {
            return Next.of(model);
        }
        return model.repo()
                .trackingBranch(remote)
                .map(local -> local.worktreeLocation()
                        .map(worktree -> Update.ask(model, new SwitchWorktreePopup(local.name(), worktree)))
                        .getOrElse(() -> local.current()
                                ? namedFollowing(model, remote)
                                : AddWorktreeUpdate.forBranch(model, local.name(), CatchUp.of(local), false)))
                .getOrElse(() -> model.repo().branch(remote.branch()).isDefined()
                        ? namedFollowing(model, remote)
                        : AddWorktreeUpdate.ask(
                                model,
                                new WorktreeSource.Created(remote.branch(), new NewBranchStart.Following(remote))));
    }

    /**
     * Asks for the name of a new branch following {@code remote}, in a new worktree, as {@code W} does.
     */
    private static Next namedFollowing(Model model, RemoteBranch remote) {
        return AddWorktreeUpdate.update(model, new AddWorktreeMsg.NewBranchAtRemoteRequested(remote));
    }

    /**
     * Whether {@code remote} is a remote branch that was read, and that no checkout of it is running for.
     */
    private static boolean isIdleRemote(Model model, RemoteBranch remote) {
        return model.repo().remoteBranch(remote).isDefined()
                && !model.jobs().busy().containsKey(new BranchRef.Remote(remote));
    }

    /**
     * Checks out {@code branch}, where the user says in a linked worktree; in a new worktree once {@code catchUp} is
     * done to it. One that another worktree has checked out git would refuse, so switching to that worktree is offered
     * instead.
     */
    private static Next requested(Model model, BranchName branch, CatchUp catchUp) {
        return model.idleBranch(branch)
                .flatMap(Branch::worktreeLocation)
                .map(worktree -> Update.ask(model, new SwitchWorktreePopup(branch, worktree)))
                .getOrElse(() -> Branches.isOtherIdleBranch(model, branch)
                        ? place(model, new CheckoutRequest.Local(branch, catchUp))
                        : Next.of(model));
    }
}
