package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.state.Update.Next;

import net.vanfleteren.jazygit.state.LoadMsg.StatusLoaded;

import net.vanfleteren.jazygit.state.LoadMsg.CommitDetailLoaded;
import net.vanfleteren.jazygit.state.LoadMsg.FileDiffLoaded;

import net.vanfleteren.jazygit.state.LoadMsg.CommitsLoaded;

import net.vanfleteren.jazygit.state.LoadMsg.BranchesLoaded;

import net.vanfleteren.jazygit.state.LoadMsg.BranchLogLoaded;

import net.vanfleteren.jazygit.state.Loadable.Loaded;

import net.vanfleteren.jazygit.state.Loadable.Failed;

import net.vanfleteren.jazygit.state.Cmd.LoadStatus;

import net.vanfleteren.jazygit.state.Cmd.LoadCommitDetail;
import net.vanfleteren.jazygit.state.Cmd.LoadFileDiff;

import net.vanfleteren.jazygit.state.Cmd.LoadCommits;

import net.vanfleteren.jazygit.state.Cmd.LoadBranches;

import net.vanfleteren.jazygit.state.Cmd.LoadBranchLog;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * What happens when something was read from the repository.
 */
public final class LoadUpdate {

    private LoadUpdate() {
    }

    public static Next update(Model model, LoadMsg msg) {
        return switch (msg) {
            case StatusLoaded(RepoStatus status) -> statusLoaded(model, status);
            case BranchesLoaded(var branches) -> branchesLoaded(model, branches);
            case CommitsLoaded(var commits) -> Next.of(model.withCommits(model.commits().reload(commits)));
            // A log that arrives after another branch was highlighted is dropped.
            case BranchLogLoaded(String branch, var commits) ->
                    Next.of(updateBranchLog(model, branch, log -> log.reload(commits)));
            // Changes that arrive after another commit was highlighted are dropped.
            case CommitDetailLoaded(String sha, String changes) ->
                    Next.of(updateCommitDetail(finished(model, new LoadCommitDetail(sha)), sha, d -> d.reload(changes)));
            // A diff that arrives after another node was highlighted is dropped.
            case FileDiffLoaded(List<FileEntry> files, Diffs diff) ->
                    Next.of(updateFileDiff(finished(model, new LoadFileDiff(files)), files, d -> d.reload(diff)));
            case LoadMsg.Failed(Cmd.Load cmd, String message) -> Next.of(failed(finished(model, cmd), cmd, message));
        };
    }

    private static Next statusLoaded(Model model, RepoStatus status) {
        Model next = finished(model, new LoadStatus()).withStatus(model.status().reload(status));
        boolean headMoved = !(model.status() instanceof Loaded<RepoStatus>(RepoStatus previous)
                && previous.headOid().equals(status.headOid()));
        // Commit loads are not deduplicated: one queued after HEAD moved must still run, even if
        // an earlier one is in progress.
        return headMoved ? Next.of(next, new LoadCommits()) : Next.of(next);
    }

    private static Next branchesLoaded(Model model, List<Branch> branches) {
        Model next = finished(model, new LoadBranches()).withBranches(model.branches().reload(branches));
        List<Branch> previous = model.branches() instanceof Loaded<List<Branch>>(List<Branch> value)
                ? value
                : List.of();
        // The highlighted branch moved (a commit, reset, fetch...): its log is outdated.
        return model.branchLog()
                .map(BranchLog::branch)
                .filter(branch -> !tip(previous, branch).equals(tip(branches, branch)))
                .map(branch -> Next.of(next, new LoadBranchLog(branch)))
                .orElseGet(() -> Next.of(next));
    }

    private static Optional<String> tip(List<Branch> branches, String name) {
        return branches.stream()
                .filter(b -> b.name().equals(name))
                .map(Branch::tipOid)
                .findFirst();
    }

    /**
     * Applies {@code f} to the branch log if it is the log of {@code branch}.
     */
    private static Model updateBranchLog(Model model, String branch, UnaryOperator<BranchLog> f) {
        return model.withBranchLog(model.branchLog().map(log -> log.branch().equals(branch) ? f.apply(log) : log));
    }

    /**
     * Applies {@code f} to the commit detail if it is the detail of {@code sha}.
     */
    private static Model updateCommitDetail(Model model, String sha, UnaryOperator<CommitDetail> f) {
        return model.withCommitDetail(model.commitDetail().map(d -> d.sha().equals(sha) ? f.apply(d) : d));
    }

    /**
     * Applies {@code f} to the file diff if it is the diff of {@code files}.
     */
    private static Model updateFileDiff(Model model, List<FileEntry> files, UnaryOperator<FileDiff> f) {
        return model.withFileDiff(model.fileDiff().map(d -> d.files().equals(files) ? f.apply(d) : d));
    }

    private static Model finished(Model model, Cmd.Load cmd) {
        return model.withRefreshing(Set.copyOf(model.refreshing().stream()
                .filter(c -> !c.equals(cmd))
                .toList()));
    }

    private static Model failed(Model model, Cmd.Load cmd, String message) {
        return switch (cmd) {
            case LoadStatus() -> model.withStatus(new Failed<>(message));
            case LoadBranches() -> model.withBranches(new Failed<>(message));
            case LoadCommits() -> model.withCommits(new Failed<>(message));
            case LoadCommitDetail(String sha) ->
                    updateCommitDetail(model, sha, d -> new CommitDetail(sha, new Failed<>(message)));
            case LoadFileDiff(List<FileEntry> files) ->
                    updateFileDiff(model, files, d -> new FileDiff(files, new Failed<>(message)));
            case LoadBranchLog(String branch) ->
                    updateBranchLog(model, branch, log -> new BranchLog(branch, new Failed<>(message)));
        };
    }
}
