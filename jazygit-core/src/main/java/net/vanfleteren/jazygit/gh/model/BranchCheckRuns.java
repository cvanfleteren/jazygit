package net.vanfleteren.jazygit.gh.model;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;

/**
 * The checks on the tips of some branches of a GitHub repository, one by one and with their timing (ADR 30).
 *
 * @param repository the repository, as {@code owner/name}
 * @param byBranch   the checks, by the name of the branch; a branch that is gone is left out
 */
public record BranchCheckRuns(String repository, Map<String, List<Check>> byBranch) {}
