package net.vanfleteren.jazygit.core.gh.checks;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.vanfleteren.jazygit.core.gh.model.*;
import net.vanfleteren.jazygit.core.gh.process.GhError;
import net.vanfleteren.jazygit.core.gh.process.GhFailure;
import net.vanfleteren.jazygit.core.gh.process.GhProcess;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;

/**
 * {@link Checks} through gh, of the repository gh finds from the git remotes of its working tree (ADR 114).
 */
public final class CliChecks implements Checks {

    private final GhProcess process;
    private final Path cache;

    /**
     * @param cache the directory the logs of checks are kept in (ADR 160)
     */
    public CliChecks(GhProcess process, Path cache) {
        this.process = process;
        this.cache = cache;
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

    @Override
    public Either<GhFailure, JobLog> jobLog(String repository, long runId, int attempt, long jobId) {
        return JobLogQuery.file(cache, repository, runId, attempt, jobId)
                .mapLeft(error -> new GhFailure(error, List.empty()))
                .flatMap(file -> Files.isRegularFile(file) ? kept(file) : download(repository, jobId, file));
    }

    /**
     * The log kept in {@code file} already.
     */
    private static Either<GhFailure, JobLog> kept(Path file) {
        return Try.of(() -> new String(Files.readAllBytes(file), StandardCharsets.UTF_8))
                .toEither()
                .mapLeft(cause -> fileFailed(file, cause, List.empty()))
                .map(text -> new JobLog(file, JobLogQuery.line(text), List.empty()));
    }

    /**
     * The log read from GitHub, and kept in {@code file}: written next to it first, then moved there, so that a log
     * cut off halfway is never taken for one kept.
     */
    private Either<GhFailure, JobLog> download(String repository, long jobId, Path file) {
        return process.run(JobLogQuery.command(repository, jobId))
                .fold(
                        failure -> JobLogQuery.tooOld(failure.error())
                                ? process.run(JobLogQuery.older(repository, jobId))
                                : Either.<GhFailure, GhProcess.Output>left(failure),
                        Either::<GhFailure, GhProcess.Output>right)
                .flatMap(output -> Try.of(() -> {
                            Path directory = Files.createDirectories(file.getParent());
                            Path part = Files.createTempFile(
                                    directory, file.getFileName().toString(), ".part");
                            Files.writeString(part, output.stdout(), StandardCharsets.UTF_8);
                            return Files.move(
                                    part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                        })
                        .toEither()
                        .mapLeft(cause -> fileFailed(file, cause, output.log()))
                        .map(kept -> new JobLog(kept, JobLogQuery.line(output.stdout()), output.log())));
    }

    private static GhFailure fileFailed(Path file, Throwable cause, List<ExecutedCommand> commands) {
        return new GhFailure(
                new GhError.FileFailed(file, cause instanceof IOException io ? io : new IOException(cause)), commands);
    }
}
