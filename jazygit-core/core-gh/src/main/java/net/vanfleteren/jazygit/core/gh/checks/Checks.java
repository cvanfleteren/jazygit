package net.vanfleteren.jazygit.core.gh.checks;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.*;
import net.vanfleteren.jazygit.core.gh.process.GhError;
import net.vanfleteren.jazygit.core.gh.process.GhFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.ForgeBranch;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.core.repo.Sha;

/**
 * How the CI checks are doing on the branches of the repository, how long they take, and which branches are
 * protected (ADR 114).
 */
public interface Checks {

    /**
     * How the CI checks are doing on the tip of each branch of the repository.
     */
    Either<GhError, BranchChecks> ofBranches();

    /**
     * The checks on the tips of {@code branches} of the repository, one by one and with their timing.
     */
    Either<GhError, BranchCheckRuns> runs(List<String> branches);

    /**
     * The checks that ran lately on the repository, to learn how long they take from: on the pull requests merged
     * last and on the last commits of its default branch.
     */
    Either<GhError, RecentChecks> recent();

    /**
     * The builds on the commits of {@code branch} of the repository, some commits at a time, the newest first: from
     * {@code after}, where the commits read before end, or from its tip without (ADR 131). None when the branch is not
     * on GitHub.
     */
    Either<GhError, Option<BuildsPage>> builds(String branch, Option<String> after);

    /**
     * The runs of the workflow {@code workflowId} on every branch, some at a time, the newest first: from
     * {@code after}, where the runs read before end, or from the newest without (ADR 131). None when it is no
     * workflow.
     */
    Either<GhError, Option<WorkflowRunsPage>> workflowRuns(String workflowId, Option<String> after);

    /**
     * The builds on the commits of the default branch from {@code after} on, or from its tip, with its name; none in a
     * repository without a commit (ADR 212).
     */
    Either<GhError, Option<DefaultBranchPage>> defaultBranchBuilds(Option<String> after);

    /**
     * How many workflow runs are in progress, on any branch (ADR 212).
     */
    Either<GhError, Integer> runsInProgress();

    /**
     * The workflows of GitHub Actions of the repository (ADR 131).
     */
    Either<GhError, List<RepoWorkflow>> workflows();

    /**
     * The file at {@code path} of the commit {@code sha} of the repository, as GitHub has it: the file of a workflow of
     * a build whose commit is not fetched (ADR 138).
     */
    Either<GhError, String> workflowFile(Sha sha, String path);

    /**
     * The names of the protected branches of the repository (ADR 72).
     */
    Either<GhError, List<ForgeBranch>> protectedBranches();

    /**
     * Runs the failed jobs of the workflow run {@code runId} of the repository again, and the jobs that need them
     * (ADR 135); the commands it ran.
     */
    Either<GhFailure, List<ExecutedCommand>> rerunFailedJobs(long runId);

    /**
     * The log of the job {@code jobId}, of the attempt {@code attempt} of the workflow run {@code runId} of
     * {@code repository}, as {@code owner/name}, kept in a file (ADR 160): read from GitHub once, and kept, as it does
     * not change once the job is done.
     */
    Either<GhFailure, JobLog> jobLog(RepoSlug repository, long runId, int attempt, long jobId);
}
