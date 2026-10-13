package net.vanfleteren.jazygit.state;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.git.model.Upstream;

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
 * @param commitDetail   what the commit highlighted in the commits pane changed (its files, a summary and its
 *                       diff), once one is highlighted
 * @param fileDiff       the diff of the node highlighted in the files pane, keyed by the changed files under
 *                       it, once one is highlighted
 * @param refreshing     periodic loads that have been started and not finished yet, so a tick does
 *                       not queue them again
 * @param error          why the last operation failed, until the next one is started
 * @param busy           the branches a git operation runs on right now, like a push or a delete, and that
 *                       operation: they show a
 *                       spinner, and take no other operation until it is done
 * @param fetching       the background fetch from the remotes that is running, if any; the next one waits
 * @param pullRequests   the open pull requests on GitHub, by the remote branch they merge from; empty when
 *                       they could not be read
 * @param pullRequestsFailure why the pull requests could not be read the last time, if they could not
 * @param readingPullRequests how many reads of the pull requests are running; the line at the bottom says so
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
                    Option<Preview<String, List<Commit>>> branchLog,
                    Option<Preview<Sha, String>> commitDetail,
                    Option<Preview<List<FileEntry>, Diffs>> fileDiff,
                    Set<Cmd.Load> refreshing,
                    Option<AppError> error,
                    Map<String, Operation> busy,
                    Option<Fetching> fetching,
                    Map<String, PullRequest> pullRequests,
                    Option<Failure> pullRequestsFailure,
                    int readingPullRequests,
                    List<LogEntry> commandLog,
                    Option<Popup> popup,
                    String theme) {

    /** The theme of a new session; one of the themes of {@code ui.Theme}. */
    public static final String DEFAULT_THEME = "dark";

    public static Model initial(String repositoryName) {
        return new Model(repositoryName, Loadable.loading(), Loadable.loading(), Loadable.loading(),
                Loadable.loading(), Option.none(),
                Option.none(), Option.none(), HashSet.empty(), Option.none(), HashMap.empty(), Option.none(), HashMap.empty(), Option.none(), 0, List.empty(),
                Option.none(), DEFAULT_THEME);
    }

    /**
     * The open popup, if it is a {@code type}.
     */
    public <P extends Popup> Option<P> popup(Class<P> type) {
        return popup.filter(type::isInstance).map(type::cast);
    }

    /**
     * The commit at {@code index} in the log of HEAD, if it is loaded and has one there.
     */
    public Option<Commit> commitAt(int index) {
        return commits.toOption().flatMap(loaded -> loaded.lift().apply(index));
    }

    /**
     * The local branch {@code name}, if the branches are loaded and it is one of them.
     */
    public Option<Branch> branch(String name) {
        return branches.toOption().flatMap(loaded -> loaded.find(branch -> branch.name().equals(name)));
    }

    /**
     * The checked out branch, if the branches are loaded and one is checked out.
     */
    public Option<Branch> currentBranch() {
        return branches.toOption().flatMap(loaded -> loaded.find(Branch::current));
    }

    /**
     * The open pull request from {@code branch}: the one from the branch it follows on the remote, else from
     * the branch of the same name.
     */
    public Option<PullRequest> pullRequest(Branch branch) {
        return branch.upstream()
                .map(Upstream::name)
                .map(name -> name.substring(name.indexOf('/') + 1))
                .flatMap(pullRequests::get)
                .orElse(() -> pullRequests.get(branch.name()));
    }

    /**
     * This model without an error shown.
     */
    public Model clearError() {
        return withError(Option.none());
    }

    /**
     * This model showing {@code error}, in the command log and in an error popup. A popup that is open stays
     * open instead, so what the user entered in it is not lost; the command log still shows the error.
     */
    public Model fail(AppError error) {
        Model failed = withError(Option.of(error));
        return popup.isDefined() ? failed : failed.openPopup(new ErrorPopup(error));
    }

    /**
     * This model with {@code operation} running on {@code branch}.
     */
    public Model withBusyBranch(String branch, Operation operation) {
        return withBusy(busy.put(branch, operation));
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
