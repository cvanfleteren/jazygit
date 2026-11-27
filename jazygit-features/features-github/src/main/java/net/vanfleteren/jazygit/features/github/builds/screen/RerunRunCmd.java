package net.vanfleteren.jazygit.features.github.builds.screen;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.features.github.GitHubServices;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * Runs the failed jobs of the workflow run {@code runId}, titled {@code title}, again, and the jobs that need them
 * (ADR 135, 212).
 */
public record RerunRunCmd(long runId, String title) implements OperationCmd {

    public static final Operation RERUN_RUN = Operation.of("github.log.title.rerunRun", "github.error.rerunRun.failed");

    @Override
    public Operation operation() {
        return RERUN_RUN;
    }

    @Override
    public Option<String> subject() {
        return Option.of(title);
    }

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public boolean changesForge() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        return GitHubServices.of(env)
                .checks()
                .rerunFailedJobs(runId)
                .mapLeft(GitHubFailure::of)
                .fold(this::failed, this::succeeded);
    }
}
