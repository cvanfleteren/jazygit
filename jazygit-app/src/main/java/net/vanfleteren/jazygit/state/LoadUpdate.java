package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.state.Cmd.LoadBranchLog;
import net.vanfleteren.jazygit.state.Cmd.LoadCommitDetail;
import net.vanfleteren.jazygit.state.Cmd.LoadCommits;
import net.vanfleteren.jazygit.state.Cmd.LoadFileDiff;
import net.vanfleteren.jazygit.state.LoadMsg.*;
import net.vanfleteren.jazygit.state.Loadable.Failed;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * What happens when something was read from the repository. The load is no longer running, whatever came of
 * it.
 */
public final class LoadUpdate {

    private LoadUpdate() {}

    public static Next update(Model model, LoadMsg msg) {
        Model done = model.withRefreshing(model.refreshing().remove(msg.cmd()));
        return switch (msg) {
            case StatusLoaded(RepoStatus status) ->
                statusLoaded(model, done.withStatus(model.status().reload(status)));
            case BranchesLoaded(var branches) ->
                branchesLoaded(model, done.withBranches(model.branches().reload(branches)));
            case RemoteBranchesLoaded(var branches) ->
                remoteBranchesLoaded(
                        model, done.withRemoteBranches(model.remoteBranches().reload(branches)));
            case StashesLoaded(var stashes) ->
                Next.of(done.withStashes(model.stashes().reload(stashes)));
            case LoadMsg.WorktreesLoaded(var worktrees) ->
                Next.of(done.withWorktrees(model.worktrees().reload(worktrees)));
            case CommitsLoaded(var commits) ->
                Next.of(done.withCommits(model.commits().reload(commits)));
            // What arrives after another item was highlighted, or after the diff options changed, is dropped.
            case BranchLogLoaded(var branch, var commits) ->
                Next.of(done.withBranchLog(Preview.updated(done.branchLog(), branch, log -> log.reload(commits))));
            case DivergenceLoaded(var branch, var other, var divergence) ->
                Next.of(done.withDivergence(done.divergence()
                        .map(view -> view.load().equals(msg.cmd())
                                ? view.withCommits(view.commits().reload(divergence))
                                : view)));
            case CommitDetailLoaded(Sha sha, var options, DiffText changes)
            when !options.equals(done.diffOptions()) -> Next.of(done);
            case CommitDetailLoaded(Sha sha, var options, DiffText changes) ->
                Next.of(withDetail(
                        done.withCommitDetail(Preview.updated(done.commitDetail(), sha, d -> d.reload(changes))),
                        sha,
                        d -> d.reload(changes)));
            case FileDiffLoaded(var files, var options, var diff)
            when !options.equals(done.diffOptions()) -> Next.of(done);
            case FileDiffLoaded(var files, var options, var diff) ->
                Next.of(done.withFileDiff(Preview.updated(done.fileDiff(), files, d -> d.reload(diff))));
            case LoadMsg.Failed(Cmd.Load cmd, Failure failure) -> Next.of(failed(done, cmd, failure));
        };
    }

    private static Next statusLoaded(Model before, Model next) {
        boolean headMoved = !before.status()
                .toOption()
                .exists(previous -> next.status()
                        .toOption()
                        .exists(status -> previous.headCommit().equals(status.headCommit())));
        // Commit loads are not deduplicated: one queued after HEAD moved must still run, even if
        // an earlier one is in progress.
        List<Cmd> cmds = headMoved ? List.of(new LoadCommits()) : List.empty();
        return Update.reloadDiff(next, cmds);
    }

    private static Next branchesLoaded(Model before, Model next) {
        return reloadDivergence(
                before,
                reloadMovedLog(
                        next,
                        branch -> branch instanceof BranchRef.Local(Branch.Name name)
                                ? Option.of(tip(before.branches(), name, Branch::name, Branch::tipOid)
                                        .equals(tip(next.branches(), name, Branch::name, Branch::tipOid)))
                                : Option.none()));
    }

    private static Next remoteBranchesLoaded(Model before, Model next) {
        return reloadDivergence(
                before,
                reloadMovedLog(
                        next,
                        branch -> branch instanceof BranchRef.Remote(RemoteBranch remote)
                                ? Option.of(tip(before.remoteBranches(), remote, RemoteRef::branch, RemoteRef::tipOid)
                                        .equals(tip(
                                                next.remoteBranches(), remote, RemoteRef::branch, RemoteRef::tipOid)))
                                : Option.none()));
    }

    /**
     * Reads the divergence that is open again when one of its branches moved, as it is outdated. When one of
     * them is gone, or the branch does not follow the upstream it is compared with anymore, it is closed: as
     * an error when the comparison cannot be made anymore, quietly when the branch itself is gone.
     */
    private static Next reloadDivergence(Model before, Next next) {
        Model model = next.model();
        return model.divergence()
                .map(view -> invalid(model, view)
                        .map(error -> new Next(
                                error.map(model.withDivergence(Option.none())::fail)
                                        .getOrElse(() -> model.withDivergence(Option.none())),
                                next.cmds()))
                        .getOrElse(() ->
                                view.refs().exists(ref -> !tipOf(before, ref).equals(tipOf(model, ref)))
                                        ? new Next(model, next.cmds().append(view.load()))
                                        : next))
                .getOrElse(next);
    }

    /**
     * Why {@code view} cannot be shown anymore, if it cannot: with the error to tell, or none when there is
     * nothing to tell.
     */
    private static Option<Option<AppError>> invalid(Model model, DivergenceView view) {
        Option<Option<Branch>> branch = model.branches().toOption().map(loaded -> model.branch(view.branch()));
        if (branch.contains(Option.none())) {
            return Option.of(Option.none());
        }
        Option<Branch> known = branch.flatMap(b -> b);
        return switch (view.against()) {
            case UPSTREAM ->
                known.filter(b ->
                                !b.upstream().map(u -> BranchRef.of(u.branch())).contains(view.other()))
                        .map(b -> b.upstream().isDefined() ? Option.none() : Option.of(AppError.noUpstream(b)));
            case BASE ->
                tipOf(model, view.other())
                        .filter(Option::isEmpty)
                        .map(gone -> Option.of(new AppError.NoBaseBranch(view.branch())));
        };
    }

    /**
     * The tip of {@code branch}, once the branches it is among are loaded: none when it is not one of them.
     */
    private static Option<Option<Sha>> tipOf(Model model, BranchRef branch) {
        return switch (branch) {
            case BranchRef.Local(Branch.Name name) ->
                model.branches().toOption().map(loaded -> model.branch(name).map(Branch::tipOid));
            case BranchRef.Remote(RemoteBranch remote) ->
                model.remoteBranches()
                        .toOption()
                        .map(loaded -> model.remoteBranch(remote).map(RemoteRef::tipOid));
        };
    }

    /**
     * Applies {@code f} to the detail of the divergence that is open, if it is the one of {@code sha}.
     */
    private static Model withDetail(Model model, Sha sha, UnaryOperator<Preview<Sha, DiffText>> f) {
        return model.withDivergence(
                model.divergence().map(view -> view.withDetail(Preview.updated(view.detail(), sha, f))));
    }

    /**
     * Reads the log of the highlighted branch again when it moved (a commit, reset, fetch...), as it is outdated.
     *
     * @param unmoved whether the highlighted branch is where it was; none when the branches that were read do
     *                not include it
     */
    private static Next reloadMovedLog(Model next, Function<BranchRef, Option<Boolean>> unmoved) {
        return next.branchLog()
                .map(Preview::key)
                .filter(branch -> unmoved.apply(branch).contains(false))
                .map(branch -> Next.of(next, new LoadBranchLog(branch)))
                .getOrElse(() -> Next.of(next));
    }

    private static <B, K> Option<Sha> tip(
            Loadable<List<B>> branches, K key, Function<B, K> keyOf, Function<B, Sha> tipOf) {
        return branches.getOrElse(List.empty())
                .find(b -> keyOf.apply(b).equals(key))
                .map(tipOf);
    }

    private static Model failed(Model model, Cmd.Load cmd, Failure failure) {
        return switch (cmd) {
            case LoadCommitDetail(Sha sha, var options) when !options.equals(model.diffOptions()) -> model;
            case LoadFileDiff(var files, var options) when !options.equals(model.diffOptions()) -> model;
            case Cmd.LoadStatus() -> model.withStatus(new Failed<>(failure));
            case Cmd.LoadBranches() -> model.withBranches(new Failed<>(failure));
            case Cmd.LoadRemoteBranches() -> model.withRemoteBranches(new Failed<>(failure));
            case Cmd.LoadStashes() -> model.withStashes(new Failed<>(failure));
            case Cmd.LoadWorktrees() -> model.withWorktrees(new Failed<>(failure));
            case LoadCommits() -> model.withCommits(new Failed<>(failure));
            case LoadCommitDetail(Sha sha, var options) ->
                withDetail(
                        model.withCommitDetail(Preview.updated(model.commitDetail(), sha, d -> d.failed(failure))),
                        sha,
                        d -> d.failed(failure));
            case Cmd.LoadDivergence load ->
                model.withDivergence(model.divergence()
                        .map(view -> view.load().equals(load) ? view.withCommits(new Failed<>(failure)) : view));
            case LoadFileDiff(var files, var options) ->
                model.withFileDiff(Preview.updated(model.fileDiff(), files, d -> d.failed(failure)));
            case LoadBranchLog(var branch) ->
                model.withBranchLog(Preview.updated(model.branchLog(), branch, log -> log.failed(failure)));
        };
    }
}
