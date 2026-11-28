package net.vanfleteren.jazygit.features.branches.branch.rebase;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.branches.branch.Branches;
import net.vanfleteren.jazygit.kernel.state.BranchBases;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.load.AheadUpdate;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;

/**
 * Rebasing the checked out branch onto another one.
 */
public final class RebaseUpdate {

    private RebaseUpdate() {}

    public static Next update(Model model, RebaseMsg msg) {
        return switch (msg) {
            case RebaseMsg.Requested(BranchRef highlighted) -> requested(model, highlighted);
            case RebaseMsg.Chosen(RebaseMsg.Target target) ->
                PopupUpdate.confirm(
                        model,
                        RebasePopup.class,
                        popup -> onto(model, popup, target).map(RebaseCmd::new));
        };
    }

    /**
     * Asks where to rebase the checked out branch, when it and {@code highlighted} are known branches;
     * {@code highlighted} can be the checked out branch itself. An option that does not apply, also while an
     * operation runs on a branch it takes, is disabled rather than keeping the popup closed (ADR 91).
     */
    private static Next requested(Model model, BranchRef highlighted) {
        return PopupUpdate.ask(
                model,
                model.repo()
                        .currentBranch()
                        .filter(current -> Branches.known(model, highlighted))
                        .map(current -> new RebasePopup(current.name(), highlighted, base(model, current))));
    }

    /**
     * Where {@code target} of {@code popup} rebases onto, when it applies: there is such a branch, and no operation
     * runs on it nor on the checked out branch.
     */
    static Option<BranchRef> onto(Model model, RebasePopup popup, RebaseMsg.Target target) {
        return popup.target(target)
                .flatMap(onto -> Branches.idle(model, onto))
                .filter(onto -> model.idleBranch(popup.branch()).isDefined())
                // A remote base is what a push or a pull of its local branch moves: that keeps it busy.
                .filter(onto -> target != RebaseMsg.Target.BASE
                        || onto instanceof BranchRef.Remote remote
                                && model.idleBranch(remote.branch().branch()).isDefined()
                        || onto instanceof BranchRef.Local);
    }

    /**
     * The base branch of {@code branch} (ADR 91): its parent (ADR 72), else the main branch; none for a root. The
     * remote-tracking one on {@code origin} when the local one follows it and has no commits of its own (ADR 214): that
     * is what the remote has, where the local branch is often stale. Else the local one, which may be rewritten here
     * and not pushed.
     */
    static Option<BranchRef> base(Model model, Branch branch) {
        Option<BranchName> main = AheadUpdate.main(model);
        if (BranchBases.in(model).root(branch.name(), main)) {
            return Option.none();
        }
        return model.repo()
                .parents()
                .get(branch.name())
                .orElse(main)
                .filter(base ->
                        !base.equals(branch.name()) && model.repo().branch(base).isDefined())
                .map(base -> remoteWhenBehind(model, base)
                        .<BranchRef>map(RemoteBranch::tracking)
                        .getOrElse(() -> new BranchRef.Local(base)));
    }

    /**
     * The branch of {@code base} on the base remote, when it is known, the local {@code base} follows it and has
     * nothing that is not on it.
     */
    private static Option<RemoteBranch> remoteWhenBehind(Model model, BranchName base) {
        return model.repo()
                .baseRemote()
                .map(remote -> new RemoteBranch(remote, base))
                .filter(remote -> model.repo().remoteBranch(remote).isDefined()
                        && model.repo()
                                .branch(base)
                                .flatMap(Branch::upstream)
                                .exists(upstream ->
                                        upstream.branch().equals(remote.tracking()) && upstream.ahead() == 0));
    }
}
