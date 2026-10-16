package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.feature.commit.ResetPopup;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import net.vanfleteren.jazygit.git.model.ResetTarget;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * The options on the upstream of the checked out branch. Resetting onto it is asked about as a reset to a
 * commit is.
 */
public final class UpstreamUpdate {

    private UpstreamUpdate() {}

    public static Next update(Model model, UpstreamMsg msg) {
        return switch (msg) {
            case UpstreamMsg.Requested() -> Update.ask(model, new UpstreamPopup(model.currentBranch()));
            case UpstreamMsg.Cancelled() -> Next.of(model.withoutPopup(UpstreamPopup.class));
            case UpstreamMsg.ResetChosen() ->
                model.popup(UpstreamPopup.class)
                        .map(popup -> resetChosen(model.withoutPopup(UpstreamPopup.class), popup))
                        .getOrElse(() -> Next.of(model));
        };
    }

    /**
     * Asks how to reset the branch onto its upstream, warning about its commits that are not on it. A branch
     * without an upstream, or whose upstream was deleted, has nothing to reset onto; with a detached HEAD there
     * is no branch to reset.
     */
    private static Next resetChosen(Model model, UpstreamPopup popup) {
        return popup.branch()
                .map(branch -> branch.upstream()
                        .map(upstream -> Update.ask(
                                model, new ResetPopup(new ResetTarget.ToUpstream(upstream.branch()), upstream.ahead())))
                        .getOrElse(() -> Next.of(model.fail(noUpstream(branch)))))
                .getOrElse(() ->
                        detached(model) ? Next.of(model.fail(new AppError.NoBranchCheckedOut())) : Next.of(model));
    }

    private static AppError noUpstream(Branch branch) {
        return branch.goneUpstream()
                .<AppError>map(upstream -> new AppError.UpstreamGone(branch.name(), upstream.shortName()))
                .getOrElse(() -> new AppError.NoUpstream(branch.name()));
    }

    private static boolean detached(Model model) {
        return model.status().toOption().exists(status -> status.head() instanceof Head.Detached);
    }
}
