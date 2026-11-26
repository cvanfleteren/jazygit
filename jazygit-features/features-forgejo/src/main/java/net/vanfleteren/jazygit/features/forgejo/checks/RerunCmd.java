package net.vanfleteren.jazygit.features.forgejo.checks;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.Forgejo;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.forgejo.ForgejoFailure;
import net.vanfleteren.jazygit.features.forgejo.builds.ForgejoServices;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * Runs the failed jobs of the runs {@code runs} on the tip of {@code branch} again, one after the other; one that
 * Forgejo refuses does not stop the others (ADR 135, 195).
 */
public record RerunCmd(RemoteBranch branch, List<RerunPlan.Run> runs) implements OperationCmd {

    public static final Operation RERUN = Operation.of("forgejo.log.title.rerun", "forgejo.error.rerun.failed");

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
    public boolean changesForge() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        Forgejo forgejo = ForgejoServices.of(env);
        return LoadChecks.repository(env, forgejo, branch.remote())
                .fold(
                        failure -> new ChecksMsg.Rerun(this, runs.map(run -> new Outcome(run, Option.of(failure)))),
                        repository -> new ChecksMsg.Rerun(
                                this,
                                runs.map(run -> new Outcome(
                                        run,
                                        run.failed()
                                                .flatMap(job -> forgejo.rerunJob(repository, job))
                                                .headOption()
                                                .map(ForgejoFailure.Asked::new)))));
    }

    /**
     * How running the failed jobs of {@code run} again went: refused, with why, or not.
     */
    public record Outcome(RerunPlan.Run run, Option<Failure> refused) {}
}
