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
 */
public record Model(String repositoryName,
                    Loadable<RepoStatus> status,
                    Loadable<List<Branch>> branches,
                    Loadable<List<Commit>> commits,
                    Optional<BranchLog> branchLog,
                    Optional<FileDiff> fileDiff,
                    Set<Cmd.Load> refreshing,
                    Optional<String> error) {

    public Model {
        refreshing = Set.copyOf(refreshing);
    }

    public static Model initial(String repositoryName) {
        return new Model(repositoryName, Loadable.loading(), Loadable.loading(), Loadable.loading(), Optional.empty(),
                Optional.empty(), Set.of(),
                Optional.empty());
    }

    Model withStatus(Loadable<RepoStatus> status) {
        return new Model(repositoryName, status, branches, commits, branchLog, fileDiff, refreshing, error);
    }

    Model withBranches(Loadable<List<Branch>> branches) {
        return new Model(repositoryName, status, branches, commits, branchLog, fileDiff, refreshing, error);
    }

    Model withCommits(Loadable<List<Commit>> commits) {
        return new Model(repositoryName, status, branches, commits, branchLog, fileDiff, refreshing, error);
    }

    Model withBranchLog(Optional<BranchLog> branchLog) {
        return new Model(repositoryName, status, branches, commits, branchLog, fileDiff, refreshing, error);
    }

    Model withFileDiff(Optional<FileDiff> fileDiff) {
        return new Model(repositoryName, status, branches, commits, branchLog, fileDiff, refreshing, error);
    }

    Model withRefreshing(Set<Cmd.Load> refreshing) {
        return new Model(repositoryName, status, branches, commits, branchLog, fileDiff, refreshing, error);
    }

    Model withError(Optional<String> error) {
        return new Model(repositoryName, status, branches, commits, branchLog, fileDiff, refreshing, error);
    }
}
