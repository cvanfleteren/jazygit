package net.vanfleteren.jazygit.core.gh.checks;

import io.vavr.collection.HashMap;
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
 * {@link Checks} for tests: a repository {@code owner/repo} without branches, checks or protected branches. Fakes
 * override what they need.
 */
public interface StubChecks extends Checks {

    /**
     * A repository {@code owner/repo} without branches.
     */
    @Override
    default Either<GhError, BranchChecks> ofBranches() {
        return Either.right(new BranchChecks(new RepoSlug("owner/repo"), HashMap.empty()));
    }

    /**
     * The repository {@code owner/repo} without checks on any branch.
     */
    @Override
    default Either<GhError, BranchCheckRuns> runs(List<String> branches) {
        return Either.right(new BranchCheckRuns(new RepoSlug("owner/repo"), HashMap.empty()));
    }

    /**
     * The repository {@code owner/repo} without checks that ran.
     */
    @Override
    default Either<GhError, RecentChecks> recent() {
        return Either.right(new RecentChecks(new RepoSlug("owner/repo"), List.empty()));
    }

    /**
     * A repository without the branch.
     */
    @Override
    default Either<GhError, Option<BuildsPage>> builds(String branch, Option<String> after) {
        return Either.right(Option.none());
    }

    /**
     * No workflow of that id.
     */
    @Override
    default Either<GhError, Option<WorkflowRunsPage>> workflowRuns(String workflowId, Option<String> after) {
        return Either.right(Option.none());
    }

    /**
     * A repository without a commit.
     */
    @Override
    default Either<GhError, Option<DefaultBranchPage>> defaultBranchBuilds(Option<String> after) {
        return Either.right(Option.none());
    }

    /**
     * No run in progress.
     */
    @Override
    default Either<GhError, Integer> runsInProgress() {
        return Either.right(0);
    }

    /**
     * Opens nothing, and says it did.
     */
    @Override
    default Either<GhFailure, List<ExecutedCommand>> openRun(long runId) {
        return Either.right(List.empty());
    }

    /**
     * A repository without workflows.
     */
    @Override
    default Either<GhError, List<RepoWorkflow>> workflows() {
        return Either.right(List.empty());
    }

    /**
     * A repository without the file: GitHub answers 404.
     */
    @Override
    default Either<GhError, String> workflowFile(Sha sha, String path) {
        return Either.left(new GhError.CommandFailed(Option.of("gh: Not Found (HTTP 404)"), 1));
    }

    /**
     * A repository that protects no branch.
     */
    @Override
    default Either<GhError, List<ForgeBranch>> protectedBranches() {
        return Either.right(List.empty());
    }

    /**
     * Runs nothing again, and says it did.
     */
    @Override
    default Either<GhFailure, List<ExecutedCommand>> rerunFailedJobs(long runId) {
        return Either.right(List.empty());
    }

    /**
     * A job without a log: GitHub answers 404.
     */
    @Override
    default Either<GhFailure, JobLog> jobLog(RepoSlug repository, long runId, int attempt, long jobId) {
        return Either.left(
                new GhFailure(new GhError.CommandFailed(Option.of("gh: Not Found (HTTP 404)"), 1), List.empty()));
    }
}
