package net.vanfleteren.jazygit.state.github;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.GitHubBases;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.github.prs.LoadRepositoryPullRequests;
import net.vanfleteren.jazygit.state.load.Load;
import net.vanfleteren.jazygit.state.load.LoadBranches;

/**
 * Reading the open pull requests from GitHub, which the branches show, along with how the checks are doing on
 * the branches without one. It runs next to the other periodic reads, after each fetch, after each push and
 * after each change made on GitHub.
 */
public final class PullRequestsUpdate {

    private PullRequestsUpdate() {}

    public static Next update(Model model, PullRequestsMsg msg) {
        return switch (msg) {
            case PullRequestsMsg.Refresh() -> load(Next.of(model));
            case PullRequestsMsg.Loaded(List<PullRequest> pullRequests) ->
                branchesAgain(
                        model,
                        GitHubReads.done(
                                FailingRead.ran(
                                        model.mapGitHub(gitHub ->
                                                gitHub.withListed(pullRequests).withPullRequestsFailure(Option.none())),
                                        new LoadRepositoryPullRequests(),
                                        false),
                                new LoadRepositoryPullRequests()));
            case PullRequestsMsg.ProtectedLoaded(var branches) ->
                branchesAgain(
                        model,
                        GitHubReads.done(
                                FailingRead.ran(
                                        model.mapGitHub(gitHub -> gitHub.withProtectedBranches(Option.of(
                                                branches.map(FromGitHub::branch).toSet()))),
                                        new LoadProtectedBranches(),
                                        false),
                                new LoadProtectedBranches()));
            // Without them, the main branch is the only root, as for a repository that protects nothing; they are
            // read again as the pull requests are, once they waited long enough (ADR 72).
            case PullRequestsMsg.ProtectedLoadFailed(var failure) ->
                GitHubReads.done(
                        FailingRead.ran(model, new LoadProtectedBranches(), true), new LoadProtectedBranches());
            // gh can be missing, logged out or offline, or the remote not on GitHub: then no pull requests show.
            // That is no reason to bother the user until they ask for a pull request. The account they chose for
            // the repository not being available is, once: gh is not run as another one.
            case PullRequestsMsg.LoadFailed(var failure) -> {
                Model failed = FailingRead.ran(
                        model.mapGitHub(gitHub -> gitHub.withPullRequestsFailure(Option.of(failure))),
                        new LoadRepositoryPullRequests(),
                        true);
                yield GitHubReads.done(
                        failure.accountUnavailable()
                                        && !model.gitHub().pullRequestsFailure().contains(failure)
                                ? failed.fail(new AppError.PullRequestsUnavailable(failure))
                                : failed,
                        new LoadRepositoryPullRequests());
            }
        };
    }

    /**
     * {@code next}, also reading the pull requests and the checks of the branches again; unless a read is still
     * running, which gets the latest state already.
     */
    public static Next load(Next next) {
        return BranchChecksUpdate.load(GitHubReads.load(protectedBranches(next), new LoadRepositoryPullRequests()));
    }

    /**
     * {@code next}, also reading the pull requests and the checks of the branches again, as a timer asks: only those
     * that do not keep failing ({@link FailingRead}), and unless a read is still running.
     */
    public static Next poll(Next next) {
        return BranchChecksUpdate.poll(GitHubReads.poll(
                unread(next) ? GitHubReads.poll(next, new LoadProtectedBranches()) : next,
                new LoadRepositoryPullRequests()));
    }

    /**
     * {@code next}, also reading the pull requests and the checks of the branches again, as something changed on
     * GitHub: when a read is still running, which may have missed that, once it is done.
     */
    public static Next loadAnyway(Next next) {
        return BranchChecksUpdate.loadAnyway(
                GitHubReads.loadAnyway(protectedBranches(next), new LoadRepositoryPullRequests()));
    }

    /**
     * {@code next}, also reading the protected branches while they were not read yet: they seldom change, so they are
     * read once while jazygit runs (ADR 72).
     */
    private static Next protectedBranches(Next next) {
        return unread(next) ? GitHubReads.load(next, new LoadProtectedBranches()) : next;
    }

    private static boolean unread(Next next) {
        return next.model().gitHub().protectedBranches().isEmpty();
    }

    /**
     * {@code next}, also reading the branches again when what GitHub says they are built on is no longer what it said
     * {@code before}: their parents and how far each is from its base go by it (ADR 72). One that runs already reads
     * as GitHub said before, and the next tick reads them again.
     */
    static Next branchesAgain(Model before, Next next) {
        Load load = LoadBranches.current(next.model(), LoadBranches.LISTED);
        return GitHubBases.of(before.gitHub())
                                .equals(GitHubBases.of(next.model().gitHub()))
                        || next.model().jobs().runs(load)
                ? next
                : next.and(load);
    }
}
