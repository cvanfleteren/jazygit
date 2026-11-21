package net.vanfleteren.jazygit.features.github.state;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.BranchBases;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;
import net.vanfleteren.jazygit.kernel.state.load.Load;
import net.vanfleteren.jazygit.kernel.state.load.LoadBranches;

/**
 * What the branches take of what GitHub says of the pull requests: the protected branches, which they are built on,
 * and the branches read again when GitHub says another base for one (ADR 72). GitHub's feature reads the open pull
 * requests themselves, with how the checks are doing on the branches without one (ADR 168).
 */
public final class PullRequestsUpdate {

    private PullRequestsUpdate() {}

    public static Next update(Model model, PullRequestsMsg msg) {
        return switch (msg) {
            case PullRequestsMsg.ProtectedLoaded(var branches) ->
                branchesAgain(
                        model,
                        GitHubReads.done(
                                FailingRead.ran(
                                        model.mapPart(
                                                GitHubState.PART,
                                                gitHub -> gitHub.withProtectedBranches(
                                                        Option.of(branches.map(FromGitHub::branch)
                                                                .toSet()))),
                                        new LoadProtectedBranches(),
                                        false),
                                new LoadProtectedBranches()));
            // Without them, the main branch is the only root, as for a repository that protects nothing; they are
            // read again as the pull requests are, once they waited long enough (ADR 72).
            case PullRequestsMsg.ProtectedLoadFailed(var failure) ->
                GitHubReads.done(
                        FailingRead.ran(model, new LoadProtectedBranches(), true), new LoadProtectedBranches());
        };
    }

    /**
     * {@code next}, also reading the protected branches while they were not read yet: they seldom change, so they are
     * read once while jazygit runs (ADR 72).
     */
    public static Next protectedBranches(Next next) {
        return unread(next) ? GitHubReads.load(next, new LoadProtectedBranches()) : next;
    }

    /**
     * {@code next}, also reading the protected branches while they were not read yet, as a timer asks: only when they
     * do not keep failing ({@link FailingRead}).
     */
    public static Next pollProtectedBranches(Next next) {
        return unread(next) ? GitHubReads.poll(next, new LoadProtectedBranches()) : next;
    }

    private static boolean unread(Next next) {
        return next.model().part(GitHubState.PART).protectedBranches().isEmpty();
    }

    /**
     * {@code next}, also reading the branches again when what GitHub says they are built on is no longer what it said
     * {@code before}: their parents and how far each is from its base go by it (ADR 72). One that runs already reads
     * as GitHub said before, and the next tick reads them again.
     */
    public static Next branchesAgain(Model before, Next next) {
        Load load = LoadBranches.current(next.model(), LoadBranches.LISTED);
        return BranchBases.in(before).equals(BranchBases.in(next.model()))
                        || next.model().jobs().runs(load)
                ? next
                : next.and(load);
    }
}
