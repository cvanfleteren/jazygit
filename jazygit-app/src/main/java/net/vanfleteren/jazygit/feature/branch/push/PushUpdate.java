package net.vanfleteren.jazygit.feature.branch.push;

import io.vavr.control.Option;
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

/**
 * Pushing a branch.
 */
public final class PushUpdate {

    private PushUpdate() {}

    public static Next update(Model model, PushMsg msg) {
        return switch (msg) {
            case PushMsg.Requested(BranchName branch) -> requested(model, branch);
            case PushMsg.ForceCancelled() -> Next.of(model.withoutPopup(ForcePushPopup.class));
            case PushMsg.UpstreamCancelled() -> Next.of(model.withoutPopup(SetUpstreamPopup.class));
            case PushMsg.UpstreamConfirmed() ->
                model.popup(SetUpstreamPopup.class)
                        .map(popup -> upstreamConfirmed(model, popup.upstream()))
                        .getOrElse(() -> Next.of(model));
            case PushMsg.ForceConfirmed() ->
                Update.confirm(
                        model,
                        ForcePushPopup.class,
                        popup -> model.repo()
                                .branch(popup.branch())
                                .flatMap(Branch::remoteUpstream)
                                .map(upstream -> new Push(popup.branch(), upstream, PushMode.FORCE_WITH_LEASE)));
        };
    }

    private static Next requested(Model model, BranchName branch) {
        // A branch that is being pushed or deleted is not pushed meanwhile. A branch without an upstream on a
        // remote (none, one that is gone, or a local branch, which a push would move) is pushed where the user
        // says. Pushing a diverged branch needs a force push, which the user must confirm.
        return model.idleBranch(branch)
                .map(known -> known.remoteUpstream()
                        .map(upstream -> known.behind() > 0
                                ? Update.ask(model, new ForcePushPopup(branch))
                                : Update.start(model.clearError(), new Push(branch, upstream, PushMode.FAST_FORWARD)))
                        .getOrElse(() -> Update.ask(model, new SetUpstreamPopup(branch))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Something other than {@code <remote> <branch>} keeps the dialog open, saying how to write it.
     */
    private static Next upstreamConfirmed(Model model, String text) {
        Option<RemoteBranch> upstream = RemoteBranch.parse(text);
        return upstream.isEmpty()
                ? Next.of(model.fail(new AppError.InvalidUpstream(text)))
                : Update.confirm(
                        model,
                        SetUpstreamPopup.class,
                        popup -> upstream.map(remote -> new PushSettingUpstream(popup.branch(), remote)));
    }
}
