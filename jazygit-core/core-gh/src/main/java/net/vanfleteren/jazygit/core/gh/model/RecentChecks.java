package net.vanfleteren.jazygit.core.gh.model;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.core.repo.RepoSlug;

/**
 * The checks that ran lately on a GitHub repository: on the last commit of the pull requests merged last, and on the
 * last commits of its default branch (ADR 32). Only check runs of a workflow, which are timed.
 *
 * @param repository the repository, as {@code owner/name}
 * @param checks     the checks, in no particular order
 */
public record RecentChecks(RepoSlug repository, List<Check> checks) {}
