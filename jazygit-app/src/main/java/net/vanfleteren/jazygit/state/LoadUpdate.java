package net.vanfleteren.jazygit.state;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.state.Cmd.LoadBranchLog;
import net.vanfleteren.jazygit.state.Cmd.LoadCommitDetail;
import net.vanfleteren.jazygit.state.Cmd.LoadCommits;
import net.vanfleteren.jazygit.state.Cmd.LoadFileDiff;
import net.vanfleteren.jazygit.state.Cmd.LoadStashDetail;
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
        Model done = model.mapJobs(jobs -> jobs.withRefreshing(jobs.refreshing().remove(msg.cmd())));
        return switch (msg) {
            // What was read with other diff options than the ones now chosen is dropped, whatever came of it.
            case LoadMsg m when stale(done, m.cmd()) -> Next.of(done);
            case StatusLoaded(RepoStatus status) ->
                statusLoaded(
                        model,
                        done.mapRepo(repo -> repo.withStatus(repo.status().reload(status))));
            case BranchesLoaded(var branches) ->
                branchesLoaded(
                        model,
                        done.mapRepo(repo -> repo.withBranches(repo.branches().reload(branches.local()))
                                .withRemoteBranches(repo.remoteBranches().reload(branches.remote()))));
            case MetadataChecked(var stamps) -> metadataChecked(done, stamps);
            case StashesLoaded(var stashes) ->
                Next.of(done.mapRepo(repo -> repo.withStashes(repo.stashes().reload(stashes))));
            case LoadMsg.WorktreesLoaded(var worktrees) ->
                Next.of(done.mapRepo(repo -> repo.withWorktrees(repo.worktrees().reload(worktrees))));
            case WorktreeChangesLoaded loaded ->
                Next.of(worktreeChangesRead(done).mapRepo(repo -> repo.withWorktreeChanges(loaded.changes())));
            // The log is only read again when HEAD moved, so the history the search runs on changed too, and what
            // is not on its base: that was read with it, so that the log is shown with it at once.
            case CommitsLoaded(var commits, var load, var ahead) ->
                commitsLoaded(AheadUpdate.read(
                        done.mapRepo(repo -> repo.withCommits(repo.commits().reload(commits))), load, ahead));
            // What arrives after another search was run, or after the search was cleared, is dropped.
            case CommitSearchLoaded(var query, var notOn, var commits) ->
                Next.of(done.mapRepoView(repoView -> repoView.withCommitSearch(repoView.commitSearch()
                        .map(search -> search.load().equals(msg.cmd())
                                ? search.withResults(search.results().reload(commits))
                                : search))));
            // What arrives after another item was highlighted, or after the diff options changed, is dropped.
            case BranchLogLoaded(var branch, var commits, var load, var ahead) ->
                done.repoView().branchLog().exists(log -> log.key().equals(branch))
                        ? AheadUpdate.branchLogLoaded(
                                done.mapRepoView(repoView -> repoView.withBranchLog(
                                        Preview.updated(repoView.branchLog(), branch, log -> log.reload(commits)))),
                                branch,
                                load,
                                ahead)
                        : Next.of(done);
            case AheadLoaded(var load, var ahead) -> AheadUpdate.loaded(done, load, ahead);
            case DivergenceLoaded(var branch, var other, var divergence) ->
                Next.of(done.mapRepoView(repoView -> repoView.withDivergence(repoView.divergence()
                        .map(view -> view.load().equals(msg.cmd())
                                ? view.withCommits(view.commits().reload(divergence))
                                : view))));
            case CommitDetailLoaded(Sha sha, var options, DiffText changes) ->
                Next.of(withDetail(
                        done.mapRepoView(repoView -> repoView.withCommitDetail(
                                Preview.updated(repoView.commitDetail(), sha, d -> d.reload(changes)))),
                        sha,
                        d -> d.reload(changes)));
            case StashDetailLoaded(Sha sha, var options, DiffText changes) ->
                Next.of(done.mapRepoView(repoView -> repoView.withStashDetail(
                        Preview.updated(repoView.stashDetail(), sha, d -> d.reload(changes)))));
            case FileDiffLoaded(var files, var options, var diff) ->
                Next.of(done.mapRepoView(repoView ->
                        repoView.withFileDiff(Preview.updated(repoView.fileDiff(), files, d -> d.reload(diff)))));
            // Without the files to tell, the loads they guard run, as they would without them.
            case LoadMsg.Failed(Cmd.CheckMetadata cmd, var failure) ->
                metadataChecked(done, Cmd.CheckMetadata.GUARDED.map(load -> Tuple.of(load, Option.none())));
            case LoadMsg.Failed(Cmd.Load cmd, Failure failure) ->
                Next.of(failed(
                        done.mapJobs(jobs -> jobs.withReadAt(jobs.readAt().remove(cmd))), cmd, failure));
        };
    }

    /**
     * Runs the search again on the log just read, unless keeping what is not on its base already did.
     */
    private static Next commitsLoaded(Next read) {
        return new Next(
                read.model(),
                read.cmds()
                        .appendAll(read.model()
                                .repoView()
                                .commitSearch()
                                .map(CommitSearch::load)
                                .filter(search -> !read.cmds().contains(search))));
    }

    /**
     * Whether {@code load} read with other diff options than the ones now chosen, so that what came of it is outdated.
     */
    private static boolean stale(Model model, Cmd.Load load) {
        return load instanceof Cmd.DiffLoad diff
                && !diff.options().equals(model.preferences().diffOptions());
    }

    /**
     * Starts the loads of {@code stamps} whose files changed since they were last started, or that cannot tell, and
     * keeps what their files look like now. One that still runs is left alone, with what it was started with: it may
     * have read before the change, so the next tick looks again.
     */
    private static Next metadataChecked(Model model, List<Tuple2<Cmd.Load, Option<MetadataStamp>>> stamps) {
        List<Tuple2<Cmd.Load, Option<MetadataStamp>>> changed = stamps.filter(
                        stamp -> !model.jobs().refreshing().contains(stamp._1()))
                .filter(stamp -> stamp._2().isEmpty()
                        || !model.jobs().readAt().get(stamp._1()).equals(stamp._2()));
        List<Cmd.Load> loads = changed.map(Tuple2::_1);
        return new Next(
                model.mapJobs(jobs -> jobs.withRefreshing(jobs.refreshing().addAll(loads))
                        .withReadAt(changed.foldLeft(
                                jobs.readAt(),
                                (readAt, stamp) -> stamp._2()
                                        .fold(() -> readAt.remove(stamp._1()), now -> readAt.put(stamp._1(), now))))),
                List.narrow(loads));
    }

    private static Next statusLoaded(Model before, Model next) {
        boolean headMoved = !before.repo()
                .status()
                .toOption()
                .exists(previous -> next.repo()
                        .status()
                        .toOption()
                        .exists(status -> previous.headCommit().equals(status.headCommit())));
        // Commit loads are not deduplicated: one queued after HEAD moved must still run, even if
        // an earlier one is in progress.
        List<Cmd> cmds = headMoved ? List.of(new LoadCommits(AheadUpdate.ofHead(next))) : List.empty();
        return Update.reloadDiff(next, cmds);
    }

    /**
     * Reads what shows a branch again when the branch moved: the log of the highlighted one, and the divergence
     * that is open, once however many of its branches moved. The commits of a branch that is gone are closed.
     */
    private static Next branchesLoaded(Model before, Model next) {
        Next reloaded = reloadDivergence(
                before,
                reloadMovedLog(
                        closeGoneBranchCommits(next),
                        branch -> !tipOf(before, branch).equals(tipOf(next, branch))));
        return new Next(reloaded.model(), reloaded.cmds().appendAll(AheadUpdate.branchesLoaded(before, next)));
    }

    /**
     * Reads the divergence that is open again when one of its branches moved, as it is outdated. When one of
     * them is gone, or the branch does not follow the upstream it is compared with anymore, it is closed: as
     * an error when the comparison cannot be made anymore, quietly when the branch itself is gone.
     */
    private static Next reloadDivergence(Model before, Next next) {
        Model model = next.model();
        return model.repoView()
                .divergence()
                .map(view -> invalid(model, view)
                        .map(error -> {
                            Model closed = model.mapRepoView(repoView -> repoView.withDivergence(Option.none()));
                            return new Next(error.map(closed::fail).getOrElse(closed), next.cmds());
                        })
                        .getOrElse(() ->
                                view.refs().exists(ref -> !tipOf(before, ref).equals(tipOf(model, ref)))
                                        ? new Next(model, next.cmds().append(view.load()))
                                        : next))
                .getOrElse(next);
    }

    /**
     * {@code model} with the commits of the branch that are open closed, quietly, if the branch is gone.
     */
    private static Model closeGoneBranchCommits(Model model) {
        return model.repoView()
                .branchCommits()
                .filter(view -> tipOf(model, view.branch()).contains(Option.none()))
                .map(gone -> model.mapRepoView(repoView -> repoView.withBranchCommits(Option.none())))
                .getOrElse(model);
    }

    /**
     * Why {@code view} cannot be shown anymore, if it cannot: with the error to tell, or none when there is
     * nothing to tell.
     */
    private static Option<Option<AppError>> invalid(Model model, DivergenceView view) {
        Option<Option<Branch>> branch =
                model.repo().branches().toOption().map(loaded -> model.repo().branch(view.branch()));
        if (branch.contains(Option.none())) {
            return Option.of(Option.none());
        }
        Option<Branch> known = branch.flatMap(b -> b);
        return switch (view.against()) {
            case UPSTREAM ->
                known.filter(b -> !b.upstream().map(Upstream::branch).contains(view.other()))
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
    static Option<Option<Sha>> tipOf(Model model, BranchRef branch) {
        return switch (branch) {
            case BranchRef.Local(Branch.Name name) ->
                model.repo()
                        .branches()
                        .toOption()
                        .map(loaded -> model.repo().branch(name).map(Branch::tipOid));
            case BranchRef.Remote(RemoteBranch remote) ->
                model.repo()
                        .remoteBranches()
                        .toOption()
                        .map(loaded -> model.repo().remoteBranch(remote).map(RemoteRef::tipOid));
        };
    }

    /**
     * Applies {@code f} to the detail of the divergence that is open, and to that of the commits of a branch that are,
     * if it is the one of {@code sha}.
     */
    private static Model withDetail(Model model, Sha sha, UnaryOperator<Preview<Sha, DiffText>> f) {
        return model.mapRepoView(repoView -> repoView.withDivergence(
                        repoView.divergence().map(view -> view.withDetail(Preview.updated(view.detail(), sha, f))))
                .withBranchCommits(
                        repoView.branchCommits().map(view -> view.withDetail(Preview.updated(view.detail(), sha, f)))));
    }

    /**
     * Reads the log of the highlighted branch again when it moved (a commit, reset, fetch...), as it is outdated.
     *
     * @param moved whether the highlighted branch is no longer where it was
     */
    private static Next reloadMovedLog(Model next, Predicate<BranchRef> moved) {
        return next.repoView()
                .branchLog()
                .map(Preview::key)
                .filter(moved)
                .map(branch -> Next.of(next, new LoadBranchLog(branch, AheadUpdate.of(next, branch))))
                .getOrElse(() -> Next.of(next));
    }

    /**
     * {@code model} with reading which worktrees have changes finished now, so that a tick waits before reading it
     * again.
     */
    private static Model worktreeChangesRead(Model model) {
        return model.mapJobs(jobs -> jobs.withWorktreeChangesRead(Option.of(model.now())));
    }

    private static Model failed(Model model, Cmd.Load cmd, Failure failure) {
        return switch (cmd) {
            case Cmd.CheckMetadata() -> model;
            case Cmd.LoadStatus() -> model.mapRepo(repo -> repo.withStatus(new Failed<>(failure)));
            case Cmd.LoadBranches() ->
                model.mapRepo(
                        repo -> repo.withBranches(new Failed<>(failure)).withRemoteBranches(new Failed<>(failure)));
            case Cmd.LoadStashes() -> model.mapRepo(repo -> repo.withStashes(new Failed<>(failure)));
            case Cmd.LoadWorktrees() -> model.mapRepo(repo -> repo.withWorktrees(new Failed<>(failure)));
            // Which worktrees have changes is a detail: when the whole read fails, what was read before stays, and
            // a later tick tries again. A single worktree that cannot be read is left out of a read that succeeds.
            case Cmd.LoadWorktreeChanges _ -> worktreeChangesRead(model);
            case LoadCommits _ -> model.mapRepo(repo -> repo.withCommits(new Failed<>(failure)));
            case Cmd.SearchCommits load ->
                model.mapRepoView(repoView -> repoView.withCommitSearch(repoView.commitSearch()
                        .map(search ->
                                search.load().equals(load) ? search.withResults(new Failed<>(failure)) : search)));
            case Cmd.LoadAhead load -> AheadUpdate.failed(model, load, failure);
            case LoadCommitDetail(Sha sha, var options) ->
                withDetail(
                        model.mapRepoView(repoView -> repoView.withCommitDetail(
                                Preview.updated(repoView.commitDetail(), sha, d -> d.failed(failure)))),
                        sha,
                        d -> d.failed(failure));
            case Cmd.LoadDivergence load ->
                model.mapRepoView(repoView -> repoView.withDivergence(repoView.divergence()
                        .map(view -> view.load().equals(load) ? view.withCommits(new Failed<>(failure)) : view)));
            case LoadFileDiff(var files, var options) ->
                model.mapRepoView(repoView ->
                        repoView.withFileDiff(Preview.updated(repoView.fileDiff(), files, d -> d.failed(failure))));
            case LoadStashDetail(Sha sha, var options) ->
                model.mapRepoView(repoView ->
                        repoView.withStashDetail(Preview.updated(repoView.stashDetail(), sha, d -> d.failed(failure))));
            case LoadBranchLog(var branch, var _) ->
                model.mapRepoView(repoView -> repoView.withBranchLog(
                        Preview.updated(repoView.branchLog(), branch, log -> log.failed(failure))));
        };
    }
}
