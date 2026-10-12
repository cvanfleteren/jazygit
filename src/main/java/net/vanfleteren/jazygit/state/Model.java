package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import lombok.With;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.Stash;

import java.util.Optional;
import java.util.Set;

/**
 * The complete, immutable state of the repository as shown by the UI.
 *
 * @param repositoryName the name of the repository
 * @param status         the working tree status and HEAD
 * @param branches       the local branches
 * @param commits        the commit log of HEAD
 * @param stashes        the stashes, newest first
 * @param branchLog      the log of the branch highlighted in the branches pane, once one is
 *                       highlighted
 * @param commitDetail   what the commit highlighted in the commits pane changed, once one is highlighted
 * @param fileDiff       the diff of the node highlighted in the files pane, once one is highlighted
 * @param refreshing     periodic loads that have been started and not finished yet, so a tick does
 *                       not queue them again
 * @param error          why the last operation failed, until the next one is started
 * @param pushing        the branches being pushed right now
 * @param commandLog     the mutating git commands that were executed, oldest first
 * @param popup          the popup that is open, if any; only one can be
 * @param theme          the name of the theme the UI is drawn in
 */
@With
public record Model(String repositoryName,
                    Loadable<RepoStatus> status,
                    Loadable<List<Branch>> branches,
                    Loadable<List<Commit>> commits,
                    Loadable<List<Stash>> stashes,
                    Optional<BranchLog> branchLog,
                    Optional<CommitDetail> commitDetail,
                    Optional<FileDiff> fileDiff,
                    Set<Cmd.Load> refreshing,
                    Optional<String> error,
                    Set<String> pushing,
                    List<LogEntry> commandLog,
                    Optional<Popup> popup,
                    String theme) {

    /** The theme of a new session; one of the themes of {@code ui.Theme}. */
    public static final String DEFAULT_THEME = "dark";

    public Model {
        refreshing = Set.copyOf(refreshing);
        pushing = Set.copyOf(pushing);
    }

    public static Model initial(String repositoryName) {
        return new Model(repositoryName, Loadable.loading(), Loadable.loading(), Loadable.loading(),
                Loadable.loading(), Optional.empty(),
                Optional.empty(), Optional.empty(), Set.of(), Optional.empty(), Set.of(), List.of(), Optional.empty(), DEFAULT_THEME);
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
