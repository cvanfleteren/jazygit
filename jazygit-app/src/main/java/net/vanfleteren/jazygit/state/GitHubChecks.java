package net.vanfleteren.jazygit.state;

import io.vavr.collection.Map;
import io.vavr.collection.Set;
import net.vanfleteren.jazygit.gh.model.BranchChecks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.git.model.BranchName;

/**
 * How the CI checks are doing on the branches of the GitHub repository, and which git remotes are that
 * repository: only their branches show these checks.
 *
 * @param remotes  the names of the remotes that point at the GitHub repository
 * @param byBranch the checks on the tip of each branch on GitHub
 */
public record GitHubChecks(Set<String> remotes, Map<BranchName, Checks> byBranch) {

    /**
     * The checks of {@code checks}, on the remotes of {@code urls}, the URL of each remote by its name, that
     * point at its repository.
     */
    public static GitHubChecks of(BranchChecks checks, Map<String, String> urls) {
        return new GitHubChecks(
                urls.filterValues(checks::isAt).keySet(), checks.checks().mapKeys(BranchName::new));
    }
}
