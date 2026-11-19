package net.vanfleteren.jazygit.core.gh.checks;

import static net.vanfleteren.jazygit.core.gh.process.GhCommand.gh;

import net.vanfleteren.jazygit.core.gh.process.GhCommand;

/**
 * Running the failed jobs of a workflow run of GitHub Actions again, and the jobs that need them, through REST, as
 * GraphQL cannot (ADR 135). gh fills in the repository of the working tree for {owner} and {repo}.
 */
final class RerunFailedJobs {

    private RerunFailedJobs() {}

    /**
     * The command that runs the failed jobs of the workflow run {@code runId} again.
     */
    static GhCommand command(long runId) {
        return gh("api", "--method", "POST", "repos/{owner}/{repo}/actions/runs/" + runId + "/rerun-failed-jobs");
    }
}
