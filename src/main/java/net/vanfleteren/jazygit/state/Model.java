package net.vanfleteren.jazygit.state;

import lombok.With;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.RepoStatus;

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
 * @param commitDetail   what the commit highlighted in the commits pane changed, once one is highlighted
 * @param fileDiff       the diff of the node highlighted in the files pane, once one is highlighted
 * @param refreshing     periodic loads that have been started and not finished yet, so a tick does
 *                       not queue them again
 * @param error          why the last operation failed, until the next one is started
 * @param pushing        the branches being pushed right now
 * @param popup          the popup that is open, if any; only one can be
 */
@With
public record Model(String repositoryName,
                    Loadable<RepoStatus> status,
                    Loadable<List<Branch>> branches,
                    Loadable<List<Commit>> commits,
                    Optional<BranchLog> branchLog,
                    Optional<CommitDetail> commitDetail,
                    Optional<FileDiff> fileDiff,
                    Set<Cmd.Load> refreshing,
                    Optional<String> error,
                    Set<String> pushing,
                    Optional<Popup> popup) {

    public Model {
        refreshing = Set.copyOf(refreshing);
        pushing = Set.copyOf(pushing);
    }

    public static Model initial(String repositoryName) {
        return new Model(repositoryName, Loadable.loading(), Loadable.loading(), Loadable.loading(), Optional.empty(),
                Optional.empty(), Optional.empty(), Set.of(), Optional.empty(), Set.of(), Optional.empty());
    }

    /**
     * The open popup, if it is a {@code type}.
     */
    public <P extends Popup> Optional<P> popup(Class<P> type) {
        return popup.filter(type::isInstance).map(type::cast);
    }

    /**
     * This model with {@code popup} open, replacing the one that was open.
     */
    public Model openPopup(Popup popup) {
        return withPopup(Optional.of(popup));
    }

    /**
     * This model with the popup closed, if the open one is a {@code type}; a different popup that
     * opened in the meantime stays open.
     */
    public Model withoutPopup(Class<? extends Popup> type) {
        return popup(type).isPresent() ? withPopup(Optional.empty()) : this;
    }
}
