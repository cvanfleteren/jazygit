package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.feature.branch.BranchCmd.Push;
import net.vanfleteren.jazygit.feature.branch.BranchCmd.PushSettingUpstream;
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
            case PushMsg.ForceConfirmed() -> model.popup(ForcePushPopup.class).map(ForcePushPopup::branch)
                    .map(branch -> push(model.withoutPopup(ForcePushPopup.class), branch, PushMode.FORCE_WITH_LEASE))
                    .getOrElse(() -> Next.of(model));
        };
    }

    private static Next requested(Model model, String branch) {
        // A branch that is being pushed or deleted is not pushed meanwhile. A branch without an upstream (or
        // whose upstream is gone) is pushed where the user says. Pushing a diverged branch needs a force push,
        // which the user must confirm.
        return model.branch(branch)
                .filter(known -> !model.busy().contains(branch))
                .map(known -> known.upstream().isEmpty()
                        ? Next.of(model.openPopup(new SetUpstreamPopup(branch)).clearError())
                        : known.behind() > 0
                        ? Next.of(model.openPopup(new ForcePushPopup(branch)).clearError())
                        : push(model, branch, PushMode.FAST_FORWARD))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Something other than {@code <remote> <branch>} keeps the dialog open, saying how to write it.
     */
    private static Next upstreamConfirmed(Model model, String text) {
        return model.popup(SetUpstreamPopup.class).map(SetUpstreamPopup::branch)
                .map(branch -> RemoteBranch.parse(text)
                        .map(upstream -> Next.of(model.withoutPopup(SetUpstreamPopup.class)
                                        .withBusyBranch(branch)
                                        .clearError(),
                                new PushSettingUpstream(branch, upstream)))
                        .getOrElse(() -> Next.of(model.fail(new AppError.InvalidUpstream(text)))))
                .getOrElse(() -> Next.of(model));
    }

    private static Next push(Model model, String branch, PushMode mode) {
        return Next.of(model.withBusyBranch(branch).clearError(),
                new Push(branch, mode));
    }

}
