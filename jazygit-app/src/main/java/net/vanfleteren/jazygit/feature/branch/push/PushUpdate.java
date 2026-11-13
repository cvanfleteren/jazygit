package net.vanfleteren.jazygit.feature.branch.push;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchError;
import net.vanfleteren.jazygit.feature.branch.push.PushCmd.Push;
import net.vanfleteren.jazygit.feature.branch.push.PushCmd.PushSettingUpstream;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.operation.OperationUpdate;

/**
 * Pushing a branch.
 */
public final class PushUpdate {

    private PushUpdate() {}

    public static Next update(Model model, PushMsg msg) {
        return switch (msg) {
            case PushMsg.Requested(BranchName branch) -> requested(model, branch);
            case PushMsg.UpstreamConfirmed() ->
                model.popup(SetUpstreamPopup.class)
                        .map(popup -> upstreamConfirmed(model, popup.upstream()))
                        .getOrElse(() -> Next.of(model));
            case PushMsg.ForceConfirmed() ->
                model.popup(ForcePushPopup.class)
                        .map(popup -> forceConfirmed(model, popup))
                        .getOrElse(() -> Next.of(model));
            case PushMsg.ForceChecked(ForcePushPopup popup, boolean had) -> forceChecked(model, popup, had);
            case PushMsg.ForceCheckFailed(BranchName branch, var failure) ->
                Next.of(model.fail(new AppError.OperationFailed(PushCmd.PUSH, Option.of(branch.value()), failure)));
            // The push ran and pushed nothing; where to push it is asked instead of the failure (ADR 40).
            case PushMsg.Refused(var cmd, var refused, var commands) ->
                Update.refresh(OperationUpdate.done(model, cmd, commands)
                        .openPopupIfFree(SetUpstreamPopup.refused(cmd.branch(), refused)));
        };
    }

    /**
     * A refresh may have changed the branch while the popup was open. Only a branch the popup still describes is force
     * pushed, as the user was warned of what that drops; otherwise the popup closes and the request is handled again,
     * which asks again or pushes as it now would.
     */
    private static Next forceConfirmed(Model model, ForcePushPopup popup) {
        boolean unchanged = model.repo()
                .branch(popup.branch())
                .flatMap(PushUpdate::forcePush)
                .contains(popup);
        return unchanged
                ? Update.confirm(
                        model,
                        ForcePushPopup.class,
                        p -> model.repo()
                                .branch(p.branch())
                                .flatMap(Branch::remoteUpstream)
                                .map(upstream -> new Push(p.branch(), upstream, PushMode.FORCE_WITH_LEASE)))
                : requested(model.withoutPopup(ForcePushPopup.class), popup.branch());
    }

    /**
     * Offers the force push of {@code popup} when the branch had what is on the remote, so that it only drops what
     * was rewritten here; otherwise says to pull it, as the remote has commits someone else pushed. A refresh may
     * have changed the branch while git was asked: one the popup no longer describes is handled again.
     */
    private static Next forceChecked(Model model, ForcePushPopup popup, boolean had) {
        boolean unchanged =
                model.idleBranch(popup.branch()).flatMap(PushUpdate::forcePush).contains(popup);
        return !unchanged
                ? requested(model, popup.branch())
                : had
                        ? Update.ask(model, popup)
                        : Next.of(model.fail(new BranchError.BehindUpstream(popup.branch(), !popup.onlyBehind())));
    }

    private static Next requested(Model model, BranchName branch) {
        // A branch that is being pushed or deleted is not pushed meanwhile. A branch without an upstream on a
        // remote (none, one that is gone, or a local branch, which a push would move) is pushed where the user
        // says. A branch behind its upstream, diverged or only behind, needs a force push, which drops commits of
        // the remote. It is offered, for the user to confirm, only when the branch once had them, as when a commit
        // was dropped or amended here; one behind what someone else pushed is to be pulled first (ADR 116). That is
        // only known of an upstream of the same name: for one of another name, push.default says where the push goes
        // when it runs, so it is pushed, and git or push.default refuse what they would (ADR 40).
        return model.idleBranch(branch)
                .map(known -> known.remoteUpstream()
                        .map(upstream -> forcePush(known)
                                .map(popup -> Next.of(model.clearError(), new ForcePushCheck(popup, upstream)))
                                .getOrElse(() -> Update.start(
                                        model.clearError(), new Push(branch, upstream, PushMode.FAST_FORWARD))))
                        .getOrElse(() -> Update.ask(model, new SetUpstreamPopup(branch))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * The force push to confirm for {@code branch}: one behind the remote branch of its own name, so that a push drops
     * commits of that one.
     */
    private static Option<ForcePushPopup> forcePush(Branch branch) {
        return sameName(branch) && branch.behind() > 0
                ? Option.of(new ForcePushPopup(branch.name(), branch.ahead(), branch.behind()))
                : Option.none();
    }

    /**
     * Whether {@code branch} follows a remote branch of its own name, which its ahead and behind are counted against.
     */
    private static boolean sameName(Branch branch) {
        return branch.remoteUpstream().exists(upstream -> upstream.branch().equals(branch.name()));
    }

    /**
     * Something other than {@code <remote> <branch>} keeps the dialog open, saying how to write it.
     */
    private static Next upstreamConfirmed(Model model, String text) {
        Option<RemoteBranch> upstream = RemoteBranch.parse(text);
        return upstream.isEmpty()
                ? Next.of(model.fail(new BranchError.InvalidUpstream(text)))
                : Update.confirm(
                        model,
                        SetUpstreamPopup.class,
                        popup -> upstream.map(remote -> new PushSettingUpstream(popup.branch(), remote)));
    }
}
