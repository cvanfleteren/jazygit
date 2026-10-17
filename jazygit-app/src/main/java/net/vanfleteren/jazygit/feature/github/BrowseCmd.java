package net.vanfleteren.jazygit.feature.github;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.OperationCmd;

/**
 * Opens {@code branch} on GitHub, in the browser.
 */
public record BrowseCmd(RemoteBranch branch) implements OperationCmd {

    public static final Operation BROWSE = Operation.of("log.title.browse", "error.browse.failed");

    @Override
    public Operation operation() {
        return BROWSE;
    }

    @Override
    public Option<String> subject() {
        return Option.of(branch.shortName());
    }

    @Override
    public Msg run(Env env) {
        return env.gitHub().browse(branch.branch().value()).fold(this::failedWith, this::succeeded);
    }
}
