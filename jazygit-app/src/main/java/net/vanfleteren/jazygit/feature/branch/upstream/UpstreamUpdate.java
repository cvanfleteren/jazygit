package net.vanfleteren.jazygit.feature.branch.upstream;

import io.vavr.control.Option;
import java.util.function.BiFunction;
import net.vanfleteren.jazygit.feature.commit.reset.ResetPopup;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Head;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.ResetTarget;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.DivergenceView;
import net.vanfleteren.jazygit.state.DivergenceView.Against;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * The options on the upstream of a branch: viewing how the highlighted branch diverged from its upstream or from
 * the main branch, which the branches pane then lists, and resetting the checked out branch onto its upstream,
 * which is asked about as a reset to a commit is.
 */
public final class UpstreamUpdate {

    private UpstreamUpdate() {}

    public static Next update(Model model, UpstreamMsg msg) {
        return switch (msg) {
            case UpstreamMsg.Requested(Option<BranchName> branch) -> {
                Option<Branch> highlighted = branch.flatMap(model.repo()::branch);
                yield Update.ask(
                        model,
                        new UpstreamPopup(
                                highlighted,
                                highlighted.flatMap(b -> base(model)),
                                model.repo().currentBranch()));
            }
            case UpstreamMsg.ResetChosen() -> chosen(model, UpstreamUpdate::resetChosen);
            case UpstreamMsg.UpstreamDivergenceChosen() -> chosen(model, UpstreamUpdate::upstreamDivergence);
            case UpstreamMsg.BaseDivergenceChosen() -> chosen(model, UpstreamUpdate::baseDivergence);
            case UpstreamMsg.DivergenceClosed() ->
                Next.of(model.mapRepoView(repoView -> repoView.withDivergence(Option.none())));
        };
    }

    /**
     * The main branch as this repository last fetched it, else as it is here; none when there is no main branch.
     */
    public static Option<BranchRef> base(Model model) {
        return model.repo()
                .branches()
                .toOption()
                .flatMap(branches -> branches.find(Branch::isDefault))
                .map(main -> model.repo()
                        .remoteBranch(new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, main.name()))
                        .<BranchRef>map(remote -> new BranchRef.Remote(remote.branch()))
                        .getOrElse(() -> new BranchRef.Local(main.name())));
    }

    private static Next chosen(Model model, BiFunction<Model, UpstreamPopup, Next> then) {
        return model.popup(UpstreamPopup.class)
                .map(popup -> then.apply(model.withoutPopup(UpstreamPopup.class), popup))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Lists the commits the highlighted branch and its upstream do not share. A branch without an upstream, or
     * whose upstream was deleted, has nothing to compare with.
     */
    private static Next upstreamDivergence(Model model, UpstreamPopup popup) {
        return popup.branch()
                .map(branch -> branch.upstream()
                        .map(upstream -> open(model, branch, upstream.branch(), Against.UPSTREAM))
                        .getOrElse(() -> Next.of(model.fail(AppError.noUpstream(branch)))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Lists the commits the highlighted branch and the main branch do not share; the main branch has nothing to
     * be compared with.
     */
    private static Next baseDivergence(Model model, UpstreamPopup popup) {
        return popup.branch()
                .map(branch -> branch.isDefault()
                        ? Next.of(model.fail(AppError.refused(
                                "error.divergence.isBase", branch.name().value())))
                        : popup.base()
                                .map(base -> open(model, branch, base, Against.BASE))
                                .getOrElse(() -> Next.of(model.fail(AppError.refused(
                                        "error.divergence.noBase", branch.name().value())))))
                .getOrElse(() -> Next.of(model));
    }

    private static Next open(Model model, Branch branch, BranchRef other, Against against) {
        DivergenceView view = DivergenceView.loading(branch.name(), other, against);
        return Next.of(
                model.clearError().mapRepoView(repoView -> repoView.withDivergence(Option.of(view))), view.load());
    }

    /**
     * Asks how to reset the checked out branch onto its upstream, warning about its commits that are not on it.
     * A branch without an upstream, or whose upstream was deleted, has nothing to reset onto; with a detached HEAD
     * there is no branch to reset.
     */
    private static Next resetChosen(Model model, UpstreamPopup popup) {
        return popup.checkedOut()
                .map(branch -> branch.upstream()
                        .map(upstream -> Update.ask(
                                model, new ResetPopup(new ResetTarget.ToUpstream(upstream.branch()), upstream.ahead())))
                        .getOrElse(() -> Next.of(model.fail(AppError.noUpstream(branch)))))
                .getOrElse(() ->
                        detached(model) ? Next.of(model.fail(AppError.refused("error.detached"))) : Next.of(model));
    }

    private static boolean detached(Model model) {
        return model.repo().status().toOption().exists(status -> status.head() instanceof Head.Detached);
    }
}
