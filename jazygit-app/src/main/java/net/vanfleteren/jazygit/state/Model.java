package net.vanfleteren.jazygit.state;

import io.vavr.collection.*;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.settings.ThemeName;

/**
 * The complete, immutable state of the repository as shown by the UI.
 *
 * @param repositoryName      the name of the repository
 * @param status              the working tree status and HEAD
 * @param branches            the local branches
 * @param remoteBranches      the remote-tracking branches
 * @param branchesTab         which branches the branches pane lists; it stays when the focus leaves the pane
 * @param commits             the commit log of HEAD
 * @param stashes             the stashes, newest first
 * @param worktrees           the worktrees of the repository, the main one first
 * @param branchLog           the log of the branch, local or remote, highlighted in the branches pane, once one is
 *                            highlighted
 * @param commitDetail        what the commit highlighted in the commits pane changed (its files, a summary and its
 *                            diff), once one is highlighted
 * @param fileDiff            the diff of the node highlighted in the files pane, keyed by the changed files under
 *                            it, once one is highlighted
 * @param refreshing          periodic loads that have been started and not finished yet, so a tick does
 *                            not queue them again
 * @param error               why the last operation failed, until the next one is started
 * @param notice              what the user is told about the last operation when it did not fail, but still needs
 *                            their attention, until the next one is started
 * @param busy                the branches a git operation runs on right now, like a push or a delete, and that
 *                            operation: they show a
 *                            spinner, and take no other operation until it is done
 * @param fetching            the background fetch from the remotes that is running, if any; the next one waits
 * @param pullRequests        the open pull requests on GitHub, by the remote branch they merge from; none until they
 *                            were read once, and the ones read last when reading them again failed
 * @param pullRequestsFailure why the pull requests could not be read the last time, if they could not
 * @param readingPullRequests how many reads of the pull requests are running; the line at the bottom says so
 * @param branchChecks        how the CI checks are doing on the branches on GitHub; none until they were read once,
 *                            and the ones read last when reading them again failed
 * @param readingBranchChecks how many reads of the checks of the branches are running
 * @param commandLog          the mutating git commands that were executed, oldest first
 * @param popup               the popup that is open, if any; only one can be
 * @param settings            what the user chose to keep from one session to the next, like the theme
 * @param themeOverride       the theme asked for at startup, or highlighted in the theme dialog, for this session only,
 *                            until the user chooses one
 * @param focusedPane         the id of the element outside the popups that had the focus last, which a closing popup
 *                            gives it back to; none before the view reported one
 * @param cherryPick          the commit copied to be cherry-picked onto the branch checked out later, if one is
 * @param divergence          the commits a local branch and its upstream or base branch do not share, which the
 *                            branches pane lists in place of the local branches while it is open
 */
@With
public record Model(
        String repositoryName,
        Loadable<RepoStatus> status,
        Loadable<List<Branch>> branches,
        Loadable<List<RemoteRef>> remoteBranches,
        BranchesTab branchesTab,
        Loadable<List<Commit>> commits,
        Loadable<List<Stash>> stashes,
        Loadable<List<Worktree>> worktrees,
        Option<Preview<BranchRef, List<Commit>>> branchLog,
        Option<Preview<Sha, DiffText>> commitDetail,
        Option<Preview<List<FileEntry>, Diffs>> fileDiff,
        Set<Cmd.Load> refreshing,
        Option<AppError> error,
        Option<Notice> notice,
        Map<BranchRef, Operation> busy,
        Option<Fetching> fetching,
        Option<Map<Branch.Name, PullRequest>> pullRequests,
        Option<Failure> pullRequestsFailure,
        int readingPullRequests,
        Option<GitHubChecks> branchChecks,
        int readingBranchChecks,
        List<LogEntry> commandLog,
        Option<Popup> popup,
        Settings settings,
        Option<ThemeName> themeOverride,
        Option<String> focusedPane,
        Option<Commit> cherryPick,
        Option<DivergenceView> divergence) {

    public static Model initial(String repositoryName) {
        return new Model(
                repositoryName,
                Loadable.loading(),
                Loadable.loading(),
                Loadable.loading(),
                BranchesTab.LOCAL,
                Loadable.loading(),
                Loadable.loading(),
                Loadable.loading(),
                Option.none(),
                Option.none(),
                Option.none(),
                HashSet.empty(),
                Option.none(),
                Option.none(),
                HashMap.empty(),
                Option.none(),
                Option.none(),
                Option.none(),
                0,
                Option.none(),
                0,
                List.empty(),
                Option.none(),
                Settings.DEFAULT,
                Option.none(),
                Option.none(),
                Option.none(),
                Option.none());
    }

    /**
     * The theme the UI is drawn in: the one asked for at startup, else the saved one.
     */
    public ThemeName theme() {
        return themeOverride.getOrElse(settings.theme());
    }

    /**
     * The open popup, if it is a {@code type}.
     */
    public <P extends Popup> Option<P> popup(Class<P> type) {
        return popup.filter(type::isInstance).map(type::cast);
    }

    /**
     * The commit {@code sha} in the log of HEAD, if the log is loaded and has it.
     */
    public Option<Commit> commit(Sha sha) {
        return commits.toOption()
                .flatMap(loaded -> loaded.find(commit -> commit.sha().equals(sha)));
    }

    /**
     * The local branch {@code name}, if the branches are loaded and it is one of them.
     */
    public Option<Branch> branch(Branch.Name name) {
        return branches.toOption()
                .flatMap(loaded -> loaded.find(branch -> branch.name().equals(name)));
    }

    /**
     * The local branch {@code name}, if it is one and no operation runs on it: a branch that is being pushed,
     * pulled or deleted takes no other operation meanwhile.
     */
    public Option<Branch> idleBranch(Branch.Name name) {
        return branch(name).filter(known -> !busy.containsKey(new BranchRef.Local(name)));
    }

    /**
     * The remote-tracking branch of {@code branch}, if the remote branches are loaded and it is one of them.
     */
    public Option<RemoteRef> remoteBranch(RemoteBranch branch) {
        return remoteBranches
                .toOption()
                .flatMap(loaded -> loaded.find(ref -> ref.branch().equals(branch)));
    }

    /**
     * The local branch that follows {@code upstream}, if the branches are loaded and one does.
     */
    public Option<Branch> trackingBranch(RemoteBranch upstream) {
        return branches.toOption()
                .flatMap(loaded -> loaded.find(branch ->
                        branch.upstream().exists(followed -> followed.branch().equals(upstream))));
    }

    /**
     * The checked out branch, if the branches are loaded and one is checked out.
     */
    public Option<Branch> currentBranch() {
        return branches.toOption().flatMap(loaded -> loaded.find(Branch::current));
    }

    /**
     * The worktree at {@code location}, if the worktrees are loaded and it is one of them.
     */
    public Option<Worktree> worktree(String location) {
        return worktrees
                .toOption()
                .flatMap(loaded -> loaded.find(worktree -> worktree.location().equals(location)));
    }

    /**
     * The main worktree, if the worktrees are loaded and the repository has one.
     */
    public Option<Worktree> mainWorktree() {
        return worktrees.toOption().flatMap(loaded -> loaded.find(Worktree::main));
    }

    /**
     * The open pull request from {@code branch}: the one from the branch it follows on the remote, else from
     * the branch of the same name; none when it follows a branch on a remote other than GitHub.
     */
    public Option<PullRequest> pullRequest(Branch branch) {
        Option<RemoteBranch> upstream = branch.remoteUpstream().map(Upstream::branch);
        return upstream.exists(followed -> !mayBeOnGitHub(followed))
                ? Option.none()
                : pullRequests.flatMap(open ->
                        upstream.map(RemoteBranch::branch).flatMap(open::get).orElse(() -> open.get(branch.name())));
    }

    /**
     * The open pull request from {@code remote}, if it is a branch on GitHub.
     */
    public Option<PullRequest> pullRequest(RemoteBranch remote) {
        return onGitHub(remote) ? pullRequests.flatMap(open -> open.get(remote.branch())) : Option.none();
    }

    /**
     * How the CI checks are doing on the branch {@code branch} follows on GitHub, if it follows one.
     */
    public Option<Checks> checks(Branch branch) {
        return branch.remoteUpstream().flatMap(upstream -> checks(upstream.branch()));
    }

    /**
     * How the CI checks are doing on {@code remote}, if it is a branch on GitHub.
     */
    public Option<Checks> checks(RemoteBranch remote) {
        return branchChecks
                .filter(checks -> checks.remotes().contains(remote.remote()))
                .flatMap(checks -> checks.byBranch().get(remote.branch()));
    }

    private boolean onGitHub(RemoteBranch remote) {
        return branchChecks.exists(checks -> checks.remotes().contains(remote.remote()));
    }

    /**
     * Whether {@code remote} is on GitHub, or could be, as which remotes are is not known yet.
     */
    private boolean mayBeOnGitHub(RemoteBranch remote) {
        return branchChecks.isEmpty() || onGitHub(remote);
    }

    /**
     * This model without an error shown, nor a notice.
     */
    public Model clearError() {
        return withError(Option.none()).withNotice(Option.none());
    }

    /**
     * This model showing {@code notice} in the command log, instead of an error.
     */
    public Model inform(Notice notice) {
        return withError(Option.none()).withNotice(Option.of(notice));
    }

    /**
     * This model showing {@code error}, in the command log and in an error popup, instead of a notice. A popup
     * that is open stays open instead, so what the user entered in it is not lost; the command log still shows the
     * error.
     */
    public Model fail(AppError error) {
        Model failed = withError(Option.of(error)).withNotice(Option.none());
        return popup.isDefined() ? failed : failed.openPopup(new ErrorPopup(error));
    }

    /**
     * This model with {@code operation} running on {@code branch}.
     */
    public Model withBusyBranch(BranchRef branch, Operation operation) {
        return withBusy(busy.put(branch, operation));
    }

    /**
     * This model with the operation on {@code branch} done.
     */
    public Model withoutBusyBranch(BranchRef branch) {
        return withBusy(busy.remove(branch));
    }

    /**
     * This model with {@code popup} open, replacing the one that was open.
     */
    public Model openPopup(Popup popup) {
        return withPopup(Option.of(popup));
    }

    /**
     * This model with {@code popup} open, unless another one is: what comes up by itself, like the outcome of a
     * command, does not take away a dialog the user is typing in.
     */
    public Model openPopupIfFree(Popup popup) {
        return this.popup.isDefined() ? this : openPopup(popup);
    }

    /**
     * This model with the popup closed, if the open one is a {@code type}; a different popup that
     * opened in the meantime stays open.
     */
    public Model withoutPopup(Class<? extends Popup> type) {
        return popup(type).isDefined() ? withPopup(Option.none()) : this;
    }
}
