package net.vanfleteren.jazygit.github.checks;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.checks.Checks;
import net.vanfleteren.jazygit.gh.process.GhFailure;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.operation.Operation;
import net.vanfleteren.jazygit.state.operation.OperationCmd;

/**
 * Runs the failed jobs of the workflow runs {@code runs} on the tip of {@code branch} again, one after the other; one
 * that GitHub refuses does not stop the others (ADR 135).
 */
public record RerunCmd(RemoteBranch branch, List<RerunPlan.Run> runs) implements OperationCmd {

    public static final Operation RERUN = Operation.of("log.title.rerun", "error.rerun.failed");

    @Override
    public Operation operation() {
        return RERUN;
    }

    @Override
    public Option<String> subject() {
        return Option.of(branch.shortName());
    }

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public boolean changesGitHub() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        Checks checks = env.gitHub().checks();
        List<Either<GhFailure, List<ExecutedCommand>>> done = runs.map(run -> checks.rerunFailedJobs(run.runId()));
        return new ChecksMsg.Rerun(
                this,
                runs.zipWith(
                        done,
                        (run, result) ->
                                new Outcome(run, result.swap().map(Failure::of).toOption())),
                done.flatMap(result -> result.fold(GhFailure::commands, commands -> commands)));
    }

    /**
     * How running {@code run} again went: refused, with why, or not.
     */
    public record Outcome(RerunPlan.Run run, Option<Failure> refused) {}
}
