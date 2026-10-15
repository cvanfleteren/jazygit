package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteRef;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Cmd.LoadBranchLog;
import net.vanfleteren.jazygit.state.Cmd.LoadCommitDetail;
import net.vanfleteren.jazygit.state.Cmd.LoadCommits;
import net.vanfleteren.jazygit.state.Cmd.LoadFileDiff;
import net.vanfleteren.jazygit.state.LoadMsg.BranchLogLoaded;
import net.vanfleteren.jazygit.state.LoadMsg.BranchesLoaded;
import net.vanfleteren.jazygit.state.LoadMsg.CommitDetailLoaded;
import net.vanfleteren.jazygit.state.LoadMsg.CommitsLoaded;
import net.vanfleteren.jazygit.state.LoadMsg.FileDiffLoaded;
import net.vanfleteren.jazygit.state.LoadMsg.RemoteBranchesLoaded;
import net.vanfleteren.jazygit.state.LoadMsg.StashesLoaded;
import net.vanfleteren.jazygit.state.LoadMsg.StatusLoaded;
import net.vanfleteren.jazygit.state.Loadable.Failed;
import net.vanfleteren.jazygit.state.Update.Next;

import java.util.function.Function;

/**
 * What happens when something was read from the repository. The load is no longer running, whatever came of
 * it.
 */
public final class LoadUpdate {

    private LoadUpdate() {
    }

    public static Next update(Model model, LoadMsg msg) {
        Model done = model.withRefreshing(model.refreshing().remove(msg.cmd()));
        return switch (msg) {
            case StatusLoaded(RepoStatus status) -> statusLoaded(model, done.withStatus(model.status().reload(status)));
            case BranchesLoaded(var branches) -> branchesLoaded(model, done.withBranches(model.branches().reload(branches)));
            case RemoteBranchesLoaded(var branches) ->
                    remoteBranchesLoaded(model, done.withRemoteBranches(model.remoteBranches().reload(branches)));
            case StashesLoaded(var stashes) -> Next.of(done.withStashes(model.stashes().reload(stashes)));
            case LoadMsg.WorktreesLoaded(var worktrees) -> Next.of(done.withWorktrees(model.worktrees().reload(worktrees)));
            case CommitsLoaded(var commits) -> Next.of(done.withCommits(model.commits().reload(commits)));
            // What arrives after another item was highlighted is dropped.
            case BranchLogLoaded(var branch, var commits) ->
                    Next.of(done.withBranchLog(Preview.updated(done.branchLog(), branch, log -> log.reload(commits))));
            case CommitDetailLoaded(Sha sha, DiffText changes) ->
                    Next.of(done.withCommitDetail(Preview.updated(done.commitDetail(), sha, d -> d.reload(changes))));
            case FileDiffLoaded(var files, var diff) ->
                    Next.of(done.withFileDiff(Preview.updated(done.fileDiff(), files, d -> d.reload(diff))));
            case LoadMsg.Failed(Cmd.Load cmd, Failure failure) -> Next.of(failed(done, cmd, failure));
        };
    }

    private static Next statusLoaded(Model before, Model next) {
        boolean headMoved = !before.status().toOption()
                .exists(previous -> next.status().toOption().exists(status -> previous.headCommit().equals(status.headCommit())));
        // Commit loads are not deduplicated: one queued after HEAD moved must still run, even if
        // an earlier one is in progress.
        List<Cmd> cmds = headMoved ? List.of(new LoadCommits()) : List.empty();
        return Update.reloadDiff(next, cmds);
    }

    private static Next branchesLoaded(Model before, Model next) {
        return reloadMovedLog(next, branch -> branch instanceof BranchRef.Local(Branch.Name name)
                ? Option.of(tip(before.branches(), name, Branch::name, Branch::tipOid)
                        .equals(tip(next.branches(), name, Branch::name, Branch::tipOid)))
                : Option.none());
    }

    private static Next remoteBranchesLoaded(Model before, Model next) {
        return reloadMovedLog(next, branch -> branch instanceof BranchRef.Remote(RemoteBranch remote)
                ? Option.of(tip(before.remoteBranches(), remote, RemoteRef::branch, RemoteRef::tipOid)
                        .equals(tip(next.remoteBranches(), remote, RemoteRef::branch, RemoteRef::tipOid)))
                : Option.none());
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

    private static <B, K> Option<Sha> tip(Loadable<List<B>> branches, K key, Function<B, K> keyOf,
                                          Function<B, Sha> tipOf) {
        return branches.getOrElse(List.empty()).find(b -> keyOf.apply(b).equals(key)).map(tipOf);
    }

    private static Model failed(Model model, Cmd.Load cmd, Failure failure) {
        return switch (cmd) {
            case Cmd.LoadStatus() -> model.withStatus(new Failed<>(failure));
            case Cmd.LoadBranches() -> model.withBranches(new Failed<>(failure));
            case Cmd.LoadRemoteBranches() -> model.withRemoteBranches(new Failed<>(failure));
            case Cmd.LoadStashes() -> model.withStashes(new Failed<>(failure));
            case Cmd.LoadWorktrees() -> model.withWorktrees(new Failed<>(failure));
            case LoadCommits() -> model.withCommits(new Failed<>(failure));
            case LoadCommitDetail(Sha sha) ->
                    model.withCommitDetail(Preview.updated(model.commitDetail(), sha, d -> d.failed(failure)));
            case LoadFileDiff(var files) ->
                    model.withFileDiff(Preview.updated(model.fileDiff(), files, d -> d.failed(failure)));
            case LoadBranchLog(var branch) ->
                    model.withBranchLog(Preview.updated(model.branchLog(), branch, log -> log.failed(failure)));
        };
    }
}
