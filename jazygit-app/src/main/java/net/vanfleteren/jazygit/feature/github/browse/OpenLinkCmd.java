package net.vanfleteren.jazygit.feature.github.browse;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.operation.Operation;
import net.vanfleteren.jazygit.state.operation.OperationCmd;

/**
 * Opens {@code url}, a link in what someone wrote, in the browser of the repository's account (ADR 96).
 */
public record OpenLinkCmd(String url) implements OperationCmd {

    public static final Operation OPEN_LINK = Operation.of("log.title.openLink", "error.openLink.failed");

    @Override
    public Operation operation() {
        return OPEN_LINK;
    }

    // The link itself, so it can be copied from the log.
    @Override
    public Option<String> subject() {
        return Option.of(url);
    }

    @Override
    public Msg run(Env env) {
        return env.gitHub().account().openLink(url).mapLeft(Failure::of).fold(this::failed, this::succeeded);
    }
}
