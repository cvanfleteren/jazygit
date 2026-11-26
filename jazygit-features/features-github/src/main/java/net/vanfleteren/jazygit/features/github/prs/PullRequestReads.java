package net.vanfleteren.jazygit.features.github.prs;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.features.github.checks.branches.BranchChecksUpdate;
import net.vanfleteren.jazygit.features.github.state.FailingRead;
import net.vanfleteren.jazygit.features.github.state.GitHubReads;
import net.vanfleteren.jazygit.features.github.state.GitHubState;
import net.vanfleteren.jazygit.features.github.state.PullRequestsError;
import net.vanfleteren.jazygit.features.github.state.PullRequestsUpdate;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;

/**
 * Reading the open pull requests of the repository from GitHub, which the branches show and the pull requests screen
 * lists (ADR 168), along with how the checks are doing on the branches without one. It runs next to the other
 * periodic reads, after each fetch, after each push and after each change made on GitHub; what it reads is kept in
 * {@link GitHubState}, which the branches read.
 */
public final class PullRequestReads {

    /**
     * The read, of which one at a time runs.
     */
    static final LoadRepositoryPullRequests READ = new LoadRepositoryPullRequests();

    private PullRequestReads() {}

    /**
     * {@code next}, also reading the pull requests and the checks of the branches again; unless a read is still
     * running, which gets the latest state already.
     */
    public static Next load(Next next) {
        return BranchChecksUpdate.load(GitHubReads.load(PullRequestsUpdate.protectedBranches(next), READ));
    }

    /**
     * {@code next}, also reading the pull requests and the checks of the branches again, as a timer asks: only those
     * that do not keep failing ({@link FailingRead}), and unless a read is still running.
     */
    public static Next poll(Next next) {
        return BranchChecksUpdate.poll(GitHubReads.poll(PullRequestsUpdate.pollProtectedBranches(next), READ));
    }

    /**
     * {@code next}, also reading the pull requests and the checks of the branches again, as something changed on
     * GitHub: when a read is still running, which may have missed that, once it is done.
     */
    public static Next loadAnyway(Next next) {
        return BranchChecksUpdate.loadAnyway(GitHubReads.loadAnyway(PullRequestsUpdate.protectedBranches(next), READ));
    }

    /**
     * {@code model} once the open pull requests of {@code repository}, {@code pullRequests}, were read: the branches
     * show them, and are read again when GitHub says another base for one (ADR 72).
     */
    public static Next loaded(Model model, List<PullRequest> pullRequests, RepoSlug repository) {
        return PullRequestsUpdate.branchesAgain(
                model,
                GitHubReads.done(
                        FailingRead.ran(
                                model.mapPart(
                                        GitHubState.PART,
                                        gitHub -> gitHub.withListed(pullRequests)
                                                .withPullRequestsFailure(Option.none())
                                                .withRepository(Option.of(repository))),
                                READ,
                                false),
                        READ));
    }

    /**
     * {@code model} once the open pull requests could not be read for {@code failure}. gh can be missing, logged out
     * or offline, or the remote not on GitHub: then no pull requests show. That is no reason to bother the user until
     * they ask for a pull request. The account they chose for the repository not being available is, once: gh is not
     * run as another one.
     */
    public static Next failed(Model model, Failure failure) {
        Model failed = FailingRead.ran(
                model.mapPart(GitHubState.PART, gitHub -> gitHub.withPullRequestsFailure(Option.of(failure))),
                READ,
                true);
        return GitHubReads.done(
                GitHubFailure.accountUnavailable(failure)
                                && !model.part(GitHubState.PART)
                                        .pullRequestsFailure()
                                        .contains(failure)
                        ? failed.fail(new PullRequestsError.Unavailable(failure))
                        : failed,
                READ);
    }
}
