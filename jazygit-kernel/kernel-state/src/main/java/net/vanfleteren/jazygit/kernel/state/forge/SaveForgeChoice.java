package net.vanfleteren.jazygit.kernel.state.forge;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * Keeps the user's choice of the home of the repository, {@code home}, or none for no forge (ADR 177), in the
 * repository's git configuration, so that it is not asked again.
 */
public record SaveForgeChoice(Option<RemoteName> home) implements OperationCmd {

    public static final Operation SAVE = Operation.of("state.log.title.forgeHome", "state.error.forgeHome.failed");

    @Override
    public Operation operation() {
        return SAVE;
    }

    @Override
    public Msg run(Env env) {
        return env.git()
                .config()
                .setLocal(
                        ReadForgeChoice.KEY,
                        Option.of(home.map(RemoteName::value).getOrElse("")))
                .fold(failure -> failed(Failure.of(failure)), this::succeeded);
    }
}
