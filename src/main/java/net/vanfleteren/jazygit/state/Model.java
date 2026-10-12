package net.vanfleteren.jazygit.state;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.Stash;

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
 * @param busy           the branches a git operation runs on right now, a push or a delete: they show a
 *                       spinner, and take no other operation until it is done
 * @param fetching       whether a background fetch from the remotes is running, so the next one waits
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
                    Option<BranchLog> branchLog,
                    Option<CommitDetail> commitDetail,
                    Option<FileDiff> fileDiff,
                    Set<Cmd.Load> refreshing,
                    Option<String> error,
                    Set<String> busy,
                    boolean fetching,
                    List<LogEntry> commandLog,
                    Option<Popup> popup,
                    String theme) {

    /** The theme of a new session; one of the themes of {@code ui.Theme}. */
    public static final String DEFAULT_THEME = "dark";

    public static Model initial(String repositoryName) {
        return new Model(repositoryName, Loadable.loading(), Loadable.loading(), Loadable.loading(),
                Loadable.loading(), Option.none(),
                Option.none(), Option.none(), HashSet.empty(), Option.none(), HashSet.empty(), false, List.empty(), Option.none(),
                DEFAULT_THEME);
    }

    /**
     * The open popup, if it is a {@code type}.
     */
    public <P extends Popup> Option<P> popup(Class<P> type) {
        return popup.filter(type::isInstance).map(type::cast);
    }

    /**
     * This model with an operation running on {@code branch}.
     */
    public Model withBusyBranch(String branch) {
        return withBusy(busy.add(branch));
    }

    /**
     * This model with the operation on {@code branch} done.
     */
    public Model withoutBusyBranch(String branch) {
        return withBusy(busy.remove(branch));
    }

    /**
     * This model with {@code popup} open, replacing the one that was open.
     */
    public Model openPopup(Popup popup) {
        return withPopup(Option.of(popup));
    }

    /**
     * This model with the popup closed, if the open one is a {@code type}; a different popup that
     * opened in the meantime stays open.
     */
    public Model withoutPopup(Class<? extends Popup> type) {
        return popup(type).isDefined() ? withPopup(Option.none()) : this;
    }
}
