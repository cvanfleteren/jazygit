package net.vanfleteren.jazygit.features.agent.investigate;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.features.agent.settings.AgentProgram;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.build.FailedBuild;
import net.vanfleteren.jazygit.kernel.state.build.ForgeAccount;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * Starts {@code agent} in the worktree at {@code worktree}, asking it to investigate {@code build} (ADR 161), as an
 * editor is started (ADR 110): with the terminal handed over to it, after which the repository is read again, like
 * after any operation, as the agent may have changed it; or started and left running. The prompt tells it the account
 * the repository uses on its forge, as that is only known as it runs (ADR 162).
 */
public record InvestigateCmd(AgentProgram agent, String worktree, FailedBuild build) implements OperationCmd {

    public static final Operation INVESTIGATE =
            Operation.of("agent.log.title.investigate", "agent.error.investigate.failed");

    @Override
    public Operation operation() {
        return INVESTIGATE;
    }

    @Override
    public Option<String> subject() {
        return Option.of(agent.name());
    }

    @Override
    public Msg run(Env env) {
        Path directory = Path.of(worktree);
        List<String> commandLine = agent.commandLine(worktree, prompt(env.forgeAccount()));
        Either<GitFailure, List<ExecutedCommand>> started = agent.terminal()
                ? env.terminal().handOver(() -> env.git().editor().open(directory, commandLine))
                : env.git().editor().launch(directory, commandLine);
        return started.mapLeft(Failure::of).fold(this::failed, this::succeeded);
    }

    /**
     * What the agent is asked, in a repository configured to use {@code account} on its forge, if any.
     */
    String prompt(Option<ForgeAccount> account) {
        return Prompt.of(build, agent.prompt(), account);
    }
}
