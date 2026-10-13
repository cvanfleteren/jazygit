package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.RepoStatus;
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
import net.vanfleteren.jazygit.state.LoadMsg.StashesLoaded;
import net.vanfleteren.jazygit.state.LoadMsg.StatusLoaded;
import net.vanfleteren.jazygit.state.Loadable.Failed;
import net.vanfleteren.jazygit.state.Update.Next;

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
            case StashesLoaded(var stashes) -> Next.of(done.withStashes(model.stashes().reload(stashes)));
            case CommitsLoaded(var commits) -> Next.of(done.withCommits(model.commits().reload(commits)));
            // What arrives after another item was highlighted is dropped.
            case BranchLogLoaded(String branch, var commits) ->
                    Next.of(done.withBranchLog(Preview.updated(done.branchLog(), branch, log -> log.reload(commits))));
            case CommitDetailLoaded(Sha sha, String changes) ->
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
        List<Branch> previous = before.branches().getOrElse(List.empty());
        List<Branch> branches = next.branches().getOrElse(List.empty());
        // The highlighted branch moved (a commit, reset, fetch...): its log is outdated.
        return next.branchLog()
                .map(Preview::key)
                .filter(branch -> !tip(previous, branch).equals(tip(branches, branch)))
                .map(branch -> Next.of(next, new LoadBranchLog(branch)))
                .getOrElse(() -> Next.of(next));
    }

    private static Option<Sha> tip(List<Branch> branches, String name) {
        return branches.find(b -> b.name().equals(name)).map(Branch::tipOid);
    }

    private static Model failed(Model model, Cmd.Load cmd, Failure failure) {
        return switch (cmd) {
            case Cmd.LoadStatus() -> model.withStatus(new Failed<>(failure));
            case Cmd.LoadBranches() -> model.withBranches(new Failed<>(failure));
            case Cmd.LoadStashes() -> model.withStashes(new Failed<>(failure));
            case LoadCommits() -> model.withCommits(new Failed<>(failure));
            case LoadCommitDetail(Sha sha) ->
                    model.withCommitDetail(Preview.updated(model.commitDetail(), sha, d -> d.failed(failure)));
            case LoadFileDiff(var files) ->
                    model.withFileDiff(Preview.updated(model.fileDiff(), files, d -> d.failed(failure)));
            case LoadBranchLog(String branch) ->
                    model.withBranchLog(Preview.updated(model.branchLog(), branch, log -> log.failed(failure)));
        };
    }
}
