package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.timing.CheckTimings;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Reading how the CI checks are doing on the branches on GitHub, which the branches without a pull request
 * show. It is read along with the pull requests.
 */
public final class BranchChecksUpdate {

    private BranchChecksUpdate() {}

    public static Next update(Model model, BranchChecksMsg msg) {
        return switch (msg) {
            // The branches whose checks are pending are read one by one too, to tell when they are done (ADR 30).
            case BranchChecksMsg.Loaded(GitHubChecks checks) -> {
                Next done = GitHubReads.done(
                        model.mapGitHub(gitHub -> gitHub.withBranchChecks(Option.of(checks))
                                .withPendingChecks(gitHub.pendingChecks().map(pending -> pending.pendingIn(checks)))),
                        new LoadBranchChecks());
                List<BranchName> pending = pending(done.model(), checks);
                yield pending.isEmpty()
                        ? done
                        : new Next(done.model(), done.cmds().append(new LoadBranchCheckRuns(pending)));
            }
            // Like for the pull requests, a failure is no reason to bother the user: the checks read last stay.
            case BranchChecksMsg.LoadFailed(var failure) -> GitHubReads.done(model, new LoadBranchChecks());
            // How long the checks take is learned from them too.
            case BranchChecksMsg.RunsLoaded(PendingChecks checks) ->
                CheckTimesUpdate.read(
                        Next.of(model.mapGitHub(gitHub -> gitHub.withPendingChecks(Option.of(checks)))),
                        checks.repository(),
                        checks.byBranch().values().toList().flatMap(runs -> runs));
            case BranchChecksMsg.RunsFailed(var failure) -> Next.of(model);
        };
    }

    /**
     * The branches on GitHub whose checks are pending in {@code checks}, at most
     * {@link CheckTimings#PENDING_BRANCHES}: the one the checked-out branch follows first, then the one the default
     * branch follows, then the others by name.
     */
    static List<BranchName> pending(Model model, GitHubChecks checks) {
        List<Branch> branches = model.repo().branches().toOption().getOrElse(List.empty());
        List<BranchName> first = List.of(branches.find(Branch::current), branches.find(Branch::isDefault))
                .flatMap(branch -> branch.flatMap(Branch::remoteUpstream))
                .filter(upstream -> checks.remotes().contains(upstream.remote()))
                .map(RemoteBranch::branch);
        List<BranchName> pending = checks.byBranch()
                .filterValues(state -> state == Checks.PENDING)
                .keySet()
                .toList()
                .sortBy(BranchName::value);
        return first.filter(pending::contains).appendAll(pending).distinct().take(CheckTimings.PENDING_BRANCHES);
    }

    /**
     * {@code next}, also reading the checks again; unless a read is still running, which gets the latest
     * state already.
     */
    static Next load(Next next) {
        return GitHubReads.load(next, new LoadBranchChecks());
    }

    /**
     * {@code next}, also reading the checks again, as something changed on GitHub: when a read is still running,
     * which may have missed that, once it is done.
     */
    static Next loadAnyway(Next next) {
        return GitHubReads.loadAnyway(next, new LoadBranchChecks());
    }
}
