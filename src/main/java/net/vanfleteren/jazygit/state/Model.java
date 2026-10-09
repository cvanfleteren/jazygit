package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.model.Branch;
import net.vanfleteren.jazygit.model.Commit;
import net.vanfleteren.jazygit.model.RepoStatus;

import java.util.List;
import java.util.Set;

/**
 * The complete, immutable state of the repository as shown by the UI.
 *
 * @param repositoryName the name of the repository
 * @param status         the working tree status and HEAD
 * @param branches       the local branches
 * @param commits        the commit log of HEAD
 * @param refreshing     periodic loads that have been started and not finished yet, so a tick does
 *                       not queue them again
 */
public record Model(String repositoryName,
                    Loadable<RepoStatus> status,
                    Loadable<List<Branch>> branches,
                    Loadable<List<Commit>> commits,
                    Set<Cmd> refreshing) {

    public Model {
        refreshing = Set.copyOf(refreshing);
    }

    public static Model initial(String repositoryName) {
        return new Model(repositoryName, Loadable.loading(), Loadable.loading(), Loadable.loading(), Set.of());
    }

    Model withStatus(Loadable<RepoStatus> status) {
        return new Model(repositoryName, status, branches, commits, refreshing);
    }

    Model withBranches(Loadable<List<Branch>> branches) {
        return new Model(repositoryName, status, branches, commits, refreshing);
    }

    Model withCommits(Loadable<List<Commit>> commits) {
        return new Model(repositoryName, status, branches, commits, refreshing);
    }

    Model withRefreshing(Set<Cmd> refreshing) {
        return new Model(repositoryName, status, branches, commits, refreshing);
    }
}
