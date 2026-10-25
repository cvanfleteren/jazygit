package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import net.vanfleteren.jazygit.gh.model.BranchCheckRuns;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.git.model.BranchName;

/**
 * The checks, one by one and with their timing, on branches on GitHub (ADR 32): those whose checks are pending, for
 * the branches pane, or the one the checks popup shows, whatever its checks are.
 *
 * @param repository the repository, as {@code owner/name}
 * @param byBranch   the checks on the tip of each of those branches
 */
public record PendingChecks(String repository, Map<BranchName, List<Check>> byBranch) {

    public static PendingChecks of(BranchCheckRuns runs) {
        return new PendingChecks(runs.repository(), runs.byBranch().mapKeys(BranchName::new));
    }

    /**
     * The checks on the tip of {@code branch}, in no particular order; none when it was not read.
     */
    public List<Check> of(BranchName branch) {
        return byBranch.get(branch).getOrElse(List.empty());
    }

    /**
     * These, of the branches that are still pending in {@code checks} only.
     */
    public PendingChecks pendingIn(GitHubChecks checks) {
        return new PendingChecks(
                repository,
                byBranch.filterKeys(branch -> checks.byBranch().get(branch).contains(Checks.PENDING)));
    }
}
