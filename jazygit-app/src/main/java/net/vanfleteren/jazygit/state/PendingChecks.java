package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import net.vanfleteren.jazygit.gh.model.BranchCheckRuns;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.git.model.BranchName;

/**
 * The checks, one by one and with their timing, on the branches on GitHub whose checks are pending (ADR 32).
 *
 * @param repository the repository, as {@code owner/name}
 * @param byBranch   the checks on the tip of each of those branches
 */
public record PendingChecks(String repository, Map<BranchName, List<Check>> byBranch) {

    public static PendingChecks of(BranchCheckRuns runs) {
        return new PendingChecks(runs.repository(), runs.byBranch().mapKeys(BranchName::new));
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
