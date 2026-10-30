package net.vanfleteren.jazygit.gh.model;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;

/**
 * The checks that ran lately on a GitHub repository: on the last commit of the pull requests merged last, and on the
 * last commits of its default branch (ADR 32). Only check runs of a workflow, which are timed.
 *
 * @param repository the repository, as {@code owner/name}
 * @param checks     the checks, in no particular order
 */
public record RecentChecks(String repository, List<Check> checks) {}
