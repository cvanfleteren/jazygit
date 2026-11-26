package net.vanfleteren.jazygit.core.gh.model;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.core.repo.ForgeBranch;
import net.vanfleteren.jazygit.core.repo.RepoSlug;

/**
 * The checks on the tips of some branches of a GitHub repository, one by one and with their timing (ADR 32).
 *
 * @param repository the repository, as {@code owner/name}
 * @param byBranch   the checks, by the name of the branch; a branch that is gone is left out
 */
public record BranchCheckRuns(RepoSlug repository, Map<ForgeBranch, List<Check>> byBranch) {}
