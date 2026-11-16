package net.vanfleteren.jazygit.gh.checks;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.*;
import net.vanfleteren.jazygit.gh.process.GhError;
import net.vanfleteren.jazygit.gh.process.GhFailure;
import net.vanfleteren.jazygit.gh.process.GhProcess;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * {@link Checks} through gh, of the repository gh finds from the git remotes of its working tree (ADR 114).
 */
public final class CliChecks implements Checks {

    private final GhProcess process;

    public CliChecks(GhProcess process) {
        this.process = process;
    }

    @Override
    public Either<GhError, BranchChecks> ofBranches() {
        return process.readJson(BranchesQuery.COMMAND, BranchesQuery.DECODER);
    }

    @Override
    public Either<GhError, BranchCheckRuns> runs(List<String> branches) {
        return process.readJson(BranchCheckRunsQuery.command(branches), BranchCheckRunsQuery.decoder(branches.size()));
    }

    @Override
    public Either<GhError, RecentChecks> recent() {
        return process.readJson(RecentChecksQuery.COMMAND, RecentChecksQuery.DECODER);
    }

    @Override
    public Either<GhError, Option<BuildsPage>> builds(String branch, Option<String> after) {
        return process.readJson(BuildsQuery.command(branch, after), BuildsQuery.DECODER);
    }

    @Override
    public Either<GhError, Option<WorkflowRunsPage>> workflowRuns(String workflowId, Option<String> after) {
        return process.readJson(WorkflowRunsQuery.command(workflowId, after), WorkflowRunsQuery.decoder(workflowId));
    }

    @Override
    public Either<GhError, List<RepoWorkflow>> workflows() {
        return process.readJson(RepoWorkflowsQuery.COMMAND, RepoWorkflowsQuery.DECODER);
    }

    @Override
    public Either<GhError, String> workflowFile(String sha, String path) {
        return process.read(WorkflowFileQuery.command(sha, path), WorkflowFileQuery.LIMIT)
                .flatMap(WorkflowFileQuery::text);
    }

    @Override
    public Either<GhError, List<GhBranch>> protectedBranches() {
        return process.readJson(ProtectedBranchesQuery.COMMAND, ProtectedBranchesQuery.DECODER);
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> rerunFailedJobs(long runId) {
        return process.log(RerunFailedJobs.command(runId));
    }
}
