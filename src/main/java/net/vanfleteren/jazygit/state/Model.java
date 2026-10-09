package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.model.Branch;
import net.vanfleteren.jazygit.model.Commit;
import net.vanfleteren.jazygit.model.RepoStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The complete, immutable state of the repository as shown by the UI.
 *
 * @param repositoryName the name of the repository
 * @param status         the working tree status and HEAD
 * @param branches       the local branches
 * @param commits        the commit log of HEAD
 * @param branchLog      the log of the branch highlighted in the branches pane, once one is
 *                       highlighted
 * @param fileDiff       the diff of the node highlighted in the files pane, once one is highlighted
 * @param refreshing     periodic loads that have been started and not finished yet, so a tick does
 *                       not queue them again
 * @param error          why the last operation failed, until the next one is started
 * @param newBranchBase  the branch a new branch is being created from, while its name is asked
 * @param deleteTarget   the branch being deleted, while the user chooses where to delete it
 * @param stageAllPrompt whether the user is asked to stage all files, because none is staged yet
 * @param commitOpen     whether the commit message is being asked
 */
public record Model(String repositoryName,
                    Loadable<RepoStatus> status,
                    Loadable<List<Branch>> branches,
                    Loadable<List<Commit>> commits,
                    Optional<BranchLog> branchLog,
                    Optional<FileDiff> fileDiff,
                    Set<Cmd.Load> refreshing,
                    Optional<String> error,
                    Optional<String> newBranchBase,
                    Optional<String> deleteTarget,
                    boolean stageAllPrompt,
                    boolean commitOpen) {

    public Model {
        refreshing = Set.copyOf(refreshing);
    }

    public static Model initial(String repositoryName) {
        return new Model(repositoryName, Loadable.loading(), Loadable.loading(), Loadable.loading(), Optional.empty(),
                Optional.empty(), Set.of(),
                Optional.empty(), Optional.empty(), Optional.empty(), false, false);
    }

    Model withStatus(Loadable<RepoStatus> status) {
        return new Model(repositoryName, status, branches, commits, branchLog, fileDiff, refreshing, error, newBranchBase, deleteTarget, stageAllPrompt, commitOpen);
    }

    Model withBranches(Loadable<List<Branch>> branches) {
        return new Model(repositoryName, status, branches, commits, branchLog, fileDiff, refreshing, error, newBranchBase, deleteTarget, stageAllPrompt, commitOpen);
    }

    Model withCommits(Loadable<List<Commit>> commits) {
        return new Model(repositoryName, status, branches, commits, branchLog, fileDiff, refreshing, error, newBranchBase, deleteTarget, stageAllPrompt, commitOpen);
    }

    Model withBranchLog(Optional<BranchLog> branchLog) {
        return new Model(repositoryName, status, branches, commits, branchLog, fileDiff, refreshing, error, newBranchBase, deleteTarget, stageAllPrompt, commitOpen);
    }

    Model withFileDiff(Optional<FileDiff> fileDiff) {
        return new Model(repositoryName, status, branches, commits, branchLog, fileDiff, refreshing, error, newBranchBase, deleteTarget, stageAllPrompt, commitOpen);
    }

    Model withRefreshing(Set<Cmd.Load> refreshing) {
        return new Model(repositoryName, status, branches, commits, branchLog, fileDiff, refreshing, error, newBranchBase, deleteTarget, stageAllPrompt, commitOpen);
    }

    Model withError(Optional<String> error) {
        return new Model(repositoryName, status, branches, commits, branchLog, fileDiff, refreshing, error, newBranchBase, deleteTarget, stageAllPrompt, commitOpen);
    }

    Model withNewBranchBase(Optional<String> newBranchBase) {
        return new Model(repositoryName, status, branches, commits, branchLog, fileDiff, refreshing, error, newBranchBase, deleteTarget, stageAllPrompt, commitOpen);
    }

    Model withDeleteTarget(Optional<String> deleteTarget) {
        return new Model(repositoryName, status, branches, commits, branchLog, fileDiff, refreshing, error, newBranchBase, deleteTarget, stageAllPrompt, commitOpen);
    }

    Model withStageAllPrompt(boolean stageAllPrompt) {
        return new Model(repositoryName, status, branches, commits, branchLog, fileDiff, refreshing, error, newBranchBase, deleteTarget, stageAllPrompt, commitOpen);
    }

    Model withCommitOpen(boolean commitOpen) {
        return new Model(repositoryName, status, branches, commits, branchLog, fileDiff, refreshing, error, newBranchBase, deleteTarget, stageAllPrompt, commitOpen);
    }
}
