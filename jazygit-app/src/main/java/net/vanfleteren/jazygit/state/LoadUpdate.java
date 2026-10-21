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
        Model done = model.mapJobs(jobs -> jobs.withRefreshing(jobs.refreshing().remove(msg.cmd())));
        return switch (msg) {
            case StatusLoaded(RepoStatus status) ->
                statusLoaded(
                        model,
                        done.mapRepo(repo -> repo.withStatus(repo.status().reload(status))));
            case AllBranchesLoaded(var branches) -> allBranchesLoaded(model, branches);
            case BranchesLoaded(var branches) ->
                branchesLoaded(
                        model,
                        done.mapRepo(repo -> repo.withBranches(repo.branches().reload(branches))));
            case RemoteBranchesLoaded(var branches) ->
                remoteBranchesLoaded(
                        model,
                        done.mapRepo(repo ->
                                repo.withRemoteBranches(repo.remoteBranches().reload(branches))));
            case StashesLoaded(var stashes) ->
                Next.of(done.mapRepo(repo -> repo.withStashes(repo.stashes().reload(stashes))));
            case LoadMsg.WorktreesLoaded(var worktrees) ->
                Next.of(done.mapRepo(repo -> repo.withWorktrees(repo.worktrees().reload(worktrees))));
            case WorktreeChangesLoaded loaded ->
                Next.of(worktreeChangesRead(done).mapRepo(repo -> repo.withWorktreeChanges(loaded.changes())));
            // The log is only read again when HEAD moved, so the history the search runs on changed too.
            case CommitsLoaded(var commits) ->
                new Next(
                        done.mapRepo(repo -> repo.withCommits(repo.commits().reload(commits))),
                        List.narrow(done.repoView()
                                .commitSearch()
                                .map(CommitSearch::load)
                                .toList()));
            // What arrives after another search was run, or after the search was cleared, is dropped.
            case CommitSearchLoaded(var query, var commits) ->
                Next.of(done.mapRepoView(repoView -> repoView.withCommitSearch(repoView.commitSearch()
                        .map(search -> search.query().equals(query)
                                ? search.withResults(search.results().reload(commits))
                                : search))));
            // What arrives after another item was highlighted, or after the diff options changed, is dropped.
            case BranchLogLoaded(var branch, var commits) ->
                Next.of(done.mapRepoView(repoView -> repoView.withBranchLog(
                        Preview.updated(repoView.branchLog(), branch, log -> log.reload(commits)))));
            case DivergenceLoaded(var branch, var other, var divergence) ->
                Next.of(done.mapRepoView(repoView -> repoView.withDivergence(repoView.divergence()
                        .map(view -> view.load().equals(msg.cmd())
                                ? view.withCommits(view.commits().reload(divergence))
                                : view))));
            case CommitDetailLoaded cl
            when !cl.options().equals(done.preferences().diffOptions()) -> Next.of(done);
            case CommitDetailLoaded(Sha sha, var options, DiffText changes) ->
                Next.of(withDetail(
                        done.mapRepoView(repoView -> repoView.withCommitDetail(
                                Preview.updated(repoView.commitDetail(), sha, d -> d.reload(changes)))),
                        sha,
                        d -> d.reload(changes)));
            case FileDiffLoaded fl when !fl.options().equals(done.preferences().diffOptions()) -> Next.of(done);
            case FileDiffLoaded(var files, var options, var diff) ->
                Next.of(done.mapRepoView(repoView ->
                        repoView.withFileDiff(Preview.updated(repoView.fileDiff(), files, d -> d.reload(diff)))));
            case LoadMsg.Failed(Cmd.Load cmd, Failure failure) -> Next.of(failed(done, cmd, failure));
        };
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
        List<Cmd> cmds = headMoved ? List.of(new LoadCommits()) : List.empty();
        return Update.reloadDiff(next, cmds);
    }

    /**
     * Takes in the local branches, then the remote-tracking ones, with what each asks for, once: a divergence of a
     * local and a remote branch that both moved is read again once.
     */
    private static Next allBranchesLoaded(Model model, AllBranches branches) {
        Next local = update(model, new BranchesLoaded(branches.local()));
        Next remote = update(local.model(), new RemoteBranchesLoaded(branches.remote()));
        return new Next(remote.model(), local.cmds().appendAll(remote.cmds()).distinct());
    }

    private static Next branchesLoaded(Model before, Model next) {
        return reloadDivergence(
                before,
                reloadMovedLog(
                        next,
                        branch -> branch instanceof BranchRef.Local(Branch.Name name)
                                ? Option.of(tip(before.repo().branches(), name, Branch::name, Branch::tipOid)
                                        .equals(tip(next.repo().branches(), name, Branch::name, Branch::tipOid)))
                                : Option.none()));
    }

    private static Next remoteBranchesLoaded(Model before, Model next) {
        return reloadDivergence(
                before,
                reloadMovedLog(
                        next,
                        branch -> branch instanceof BranchRef.Remote(RemoteBranch remote)
                                ? Option.of(tip(
                                                before.repo().remoteBranches(),
                                                remote,
                                                RemoteRef::branch,
                                                RemoteRef::tipOid)
                                        .equals(tip(
                                                next.repo().remoteBranches(),
                                                remote,
                                                RemoteRef::branch,
                                                RemoteRef::tipOid)))
                                : Option.none()));
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
     * Applies {@code f} to the detail of the divergence that is open, if it is the one of {@code sha}.
     */
    private static Model withDetail(Model model, Sha sha, UnaryOperator<Preview<Sha, DiffText>> f) {
        return model.mapRepoView(repoView -> repoView.withDivergence(
                repoView.divergence().map(view -> view.withDetail(Preview.updated(view.detail(), sha, f)))));
    }

    /**
     * Reads the log of the highlighted branch again when it moved (a commit, reset, fetch...), as it is outdated.
     *
     * @param unmoved whether the highlighted branch is where it was; none when the branches that were read do
     *                not include it
     */
    private static Next reloadMovedLog(Model next, Function<BranchRef, Option<Boolean>> unmoved) {
        return next.repoView()
                .branchLog()
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

    /**
     * {@code model} with reading which worktrees have changes finished now, so that a tick waits before reading it
     * again.
     */
    private static Model worktreeChangesRead(Model model) {
        return model.mapJobs(jobs -> jobs.withWorktreeChangesRead(Option.of(model.now())));
    }

    private static Model failed(Model model, Cmd.Load cmd, Failure failure) {
        return switch (cmd) {
            case LoadCommitDetail(Sha sha, var options)
            when !options.equals(model.preferences().diffOptions()) -> model;
            case LoadFileDiff(var files, var options)
            when !options.equals(model.preferences().diffOptions()) -> model;
            case Cmd.LoadStatus() -> model.mapRepo(repo -> repo.withStatus(new Failed<>(failure)));
            case Cmd.LoadBranches() ->
                model.mapRepo(
                        repo -> repo.withBranches(new Failed<>(failure)).withRemoteBranches(new Failed<>(failure)));
            case Cmd.LoadStashes() -> model.mapRepo(repo -> repo.withStashes(new Failed<>(failure)));
            case Cmd.LoadWorktrees() -> model.mapRepo(repo -> repo.withWorktrees(new Failed<>(failure)));
            // Which worktrees have changes is a detail: when the whole read fails, what was read before stays, and
            // a later tick tries again. A single worktree that cannot be read is left out of a read that succeeds.
            case Cmd.LoadWorktreeChanges _ -> worktreeChangesRead(model);
            case LoadCommits() -> model.mapRepo(repo -> repo.withCommits(new Failed<>(failure)));
            case Cmd.SearchCommits(var query) ->
                model.mapRepoView(repoView -> repoView.withCommitSearch(repoView.commitSearch()
                        .map(search ->
                                search.query().equals(query) ? search.withResults(new Failed<>(failure)) : search)));
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
            case LoadBranchLog(var branch) ->
                model.mapRepoView(repoView -> repoView.withBranchLog(
                        Preview.updated(repoView.branchLog(), branch, log -> log.failed(failure))));
        };
    }
}
