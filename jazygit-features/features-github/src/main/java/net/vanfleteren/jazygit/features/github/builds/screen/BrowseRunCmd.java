package net.vanfleteren.jazygit.features.github.builds.screen;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.features.github.GitHubServices;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * Opens the workflow run {@code runId}, titled {@code title}, in the browser set for the repository's account (ADR
 * 88, 212).
 */
public record BrowseRunCmd(long runId, String title) implements OperationCmd {

    public static final Operation OPEN_RUN = Operation.of("github.log.title.openRun", "github.error.openRun.failed");

    @Override
    public Operation operation() {
        return OPEN_RUN;
    }

    @Override
    public Option<String> subject() {
        return Option.of(title);
    }

    @Override
    public Msg run(Env env) {
        return GitHubServices.of(env)
                .account()
                .openRun(runId)
                .mapLeft(GitHubFailure::of)
                .fold(this::failed, this::succeeded);
    }
}
