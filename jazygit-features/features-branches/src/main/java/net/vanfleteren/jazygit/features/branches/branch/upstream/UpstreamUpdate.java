package net.vanfleteren.jazygit.features.branches.branch.upstream;

import io.vavr.control.Option;
import java.util.function.BiFunction;
import net.vanfleteren.jazygit.core.git.model.ResetTarget;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.Head;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Tracking;
import net.vanfleteren.jazygit.features.branches.branch.BranchError;
import net.vanfleteren.jazygit.features.changes.commit.reset.ResetPopup;
import net.vanfleteren.jazygit.kernel.state.BaseBranchError;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.UpstreamError;
import net.vanfleteren.jazygit.kernel.state.load.LoadDivergence;
import net.vanfleteren.jazygit.kernel.state.operation.OperationUpdate;
import net.vanfleteren.jazygit.kernel.state.popup.PopupUpdate;
import net.vanfleteren.jazygit.kernel.state.view.DivergenceView;
import net.vanfleteren.jazygit.kernel.state.view.DivergenceView.Against;

/**
 * The options on the upstream of a branch: viewing how the highlighted branch diverged from its upstream or from
 * the main branch, which the branches pane then lists, and resetting the checked out branch onto its upstream,
 * which is asked about as a reset to a commit is, and setting or unsetting the upstream of the highlighted branch.
 */
public final class UpstreamUpdate {

    private UpstreamUpdate() {}

    public static Next update(Model model, UpstreamMsg msg) {
        return switch (msg) {
            case UpstreamMsg.Requested(Option<BranchName> branch) -> {
                Option<Branch> highlighted = branch.flatMap(model.repo()::branch);
                yield PopupUpdate.ask(
                        model,
                        new UpstreamPopup(
                                highlighted,
                                highlighted.flatMap(b -> base(model)),
                                model.repo().currentBranch()));
            }
            case UpstreamMsg.SetChosen() -> chosen(model, UpstreamUpdate::setChosen);
            case UpstreamMsg.SetConfirmed() -> setConfirmed(model);
            case UpstreamMsg.UnsetChosen() -> chosen(model, UpstreamUpdate::unsetChosen);
            case UpstreamMsg.ResetChosen() -> chosen(model, UpstreamUpdate::resetChosen);
            case UpstreamMsg.UpstreamDivergenceChosen() -> chosen(model, UpstreamUpdate::upstreamDivergence);
            case UpstreamMsg.BaseDivergenceChosen() -> chosen(model, UpstreamUpdate::baseDivergence);
            case UpstreamMsg.DivergenceClosed() ->
                Next.of(model.mapRepoView(
                        repoView -> repoView.mapBranches(branches -> branches.withDivergence(Option.none()))));
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
     * Asks which remote branch the highlighted branch is to follow, starting out with the one of its own name.
     */
    private static Next setChosen(Model model, UpstreamPopup popup) {
        return PopupUpdate.ask(
                model,
                popup.branch()
                        .flatMap(branch -> model.idleBranch(branch.name()))
                        .map(ChangeUpstreamPopup::of));
    }

    /**
     * Makes the branch follow what was entered. Something other than {@code <remote> <branchname>} keeps the dialog
     * open, saying how to write it, as does a branch that is not on the remote as it was last fetched: git needs it
     * there, and says so less clearly.
     */
    private static Next setConfirmed(Model model) {
        return model.popup(ChangeUpstreamPopup.class)
                .map(popup -> RemoteBranch.parse(popup.text())
                        .map(upstream -> notThere(model, upstream)
                                ? Next.of(model.fail(new BranchError.UpstreamMissing(upstream)))
                                : PopupUpdate.confirm(
                                        model,
                                        ChangeUpstreamPopup.class,
                                        p -> model.idleBranch(p.branch())
                                                .map(known -> new UpstreamCmd.Set(known.name(), upstream))))
                        .getOrElse(() -> Next.of(model.fail(new BranchError.InvalidUpstream(popup.text())))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Whether the remote branches are read, and {@code upstream} is not among them.
     */
    private static boolean notThere(Model model, RemoteBranch upstream) {
        return model.repo()
                .remoteBranches()
                .toOption()
                .exists(refs -> !refs.exists(ref -> ref.branch().equals(upstream)));
    }

    /**
     * Makes the highlighted branch follow nothing. One that follows nothing has nothing to unset; one whose upstream
     * was deleted on the remote still has it configured, which this removes.
     */
    private static Next unsetChosen(Model model, UpstreamPopup popup) {
        return popup.branch()
                .flatMap(branch -> model.idleBranch(branch.name()))
                .map(branch -> branch.tracking() instanceof Tracking.None
                        ? Next.of(model.fail(UpstreamError.of(branch)))
                        : OperationUpdate.start(model.clearError(), new UpstreamCmd.Unset(branch.name())))
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
                        .getOrElse(() -> Next.of(model.fail(UpstreamError.of(branch)))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Lists the commits the highlighted branch and the main branch do not share; the main branch has nothing to
     * be compared with.
     */
    private static Next baseDivergence(Model model, UpstreamPopup popup) {
        return popup.branch()
                .map(branch -> branch.isDefault()
                        ? Next.of(model.fail(new BaseBranchError.IsBase(branch.name())))
                        : popup.base()
                                .map(base -> open(model, branch, base, Against.BASE))
                                .getOrElse(() -> Next.of(model.fail(new BaseBranchError.None(branch.name())))))
                .getOrElse(() -> Next.of(model));
    }

    private static Next open(Model model, Branch branch, BranchRef other, Against against) {
        DivergenceView view = DivergenceView.loading(branch.name(), other, against);
        return Next.of(
                model.clearError()
                        .mapRepoView(
                                repoView -> repoView.mapBranches(branches -> branches.withDivergence(Option.of(view)))),
                LoadDivergence.of(view));
    }

    /**
     * Asks how to reset the checked out branch onto its upstream, warning about its commits that are not on it.
     * A branch without an upstream, or whose upstream was deleted, has nothing to reset onto; with a detached HEAD
     * there is no branch to reset.
     */
    private static Next resetChosen(Model model, UpstreamPopup popup) {
        return popup.checkedOut()
                .map(branch -> branch.upstream()
                        .map(upstream -> PopupUpdate.ask(
                                model, new ResetPopup(new ResetTarget.ToUpstream(upstream.branch()), upstream.ahead())))
                        .getOrElse(() -> Next.of(model.fail(UpstreamError.of(branch)))))
                .getOrElse(() ->
                        detached(model) ? Next.of(model.fail(new BranchError.NoBranchCheckedOut())) : Next.of(model));
    }

    private static boolean detached(Model model) {
        return model.repo().status().toOption().exists(status -> status.head() instanceof Head.Detached);
    }
}
