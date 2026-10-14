package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.state.Update;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.Push;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.PushSettingUpstream;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Pushing a branch.
 */
public final class PushUpdate {

    private PushUpdate() {
    }

    public static Next update(Model model, PushMsg msg) {
        return switch (msg) {
            case PushMsg.Requested(String branch) -> requested(model, branch);
            case PushMsg.ForceCancelled() -> Next.of(model.withoutPopup(ForcePushPopup.class));
            case PushMsg.UpstreamCancelled() -> Next.of(model.withoutPopup(SetUpstreamPopup.class));
            case PushMsg.UpstreamConfirmed(String upstream) -> upstreamConfirmed(model, upstream);
            case PushMsg.ForceConfirmed() -> Update.confirm(model, ForcePushPopup.class,
                    popup -> model.branch(popup.branch()).flatMap(Branch::remoteUpstream)
                            .map(upstream -> new Push(popup.branch(), upstream.branch(), PushMode.FORCE_WITH_LEASE)));
        };
    }

    private static Next requested(Model model, String branch) {
        // A branch that is being pushed or deleted is not pushed meanwhile. A branch without an upstream on a
        // remote (none, one that is gone, or a local branch, which a push would move) is pushed where the user
        // says. Pushing a diverged branch needs a force push, which the user must confirm.
        return model.idleBranch(branch)
                .map(known -> known.remoteUpstream()
                        .map(upstream -> known.behind() > 0
                                ? Next.of(model.openPopup(new ForcePushPopup(branch)).clearError())
                                : Update.start(model.clearError(), new Push(branch, upstream.branch(), PushMode.FAST_FORWARD)))
                        .getOrElse(() -> Next.of(model.openPopup(new SetUpstreamPopup(branch)).clearError())))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Something other than {@code <remote> <branch>} keeps the dialog open, saying how to write it.
     */
    private static Next upstreamConfirmed(Model model, String text) {
        Option<RemoteBranch> upstream = RemoteBranch.parse(text);
        return model.popup(SetUpstreamPopup.class).isDefined() && upstream.isEmpty()
                ? Next.of(model.fail(new AppError.InvalidUpstream(text)))
                : Update.confirm(model, SetUpstreamPopup.class,
                popup -> upstream.map(remote -> new PushSettingUpstream(popup.branch(), remote)));
    }


}
