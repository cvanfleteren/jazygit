package net.vanfleteren.jazygit.features.github.browse;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * Opens {@code url}, a link in what someone wrote, in the browser of the repository's account (ADR 96).
 */
public record OpenLinkCmd(String url) implements OperationCmd {

    public static final Operation OPEN_LINK = Operation.of("github.log.title.openLink", "github.error.openLink.failed");

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
        return env.gitHub().account().openLink(url).mapLeft(GitHubFailure::of).fold(this::failed, this::succeeded);
    }
}
