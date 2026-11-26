package net.vanfleteren.jazygit.features.forgejo.browse;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.features.forgejo.builds.ForgejoServices;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * Opens the pull request at {@code url} on Forgejo in the browser (ADR 176).
 */
public record OpenPullRequestCmd(String url) implements OperationCmd {

    public static final Operation OPEN =
            Operation.of("forgejo.log.title.openPullRequest", "forgejo.error.openPullRequest.failed");

    @Override
    public Operation operation() {
        return OPEN;
    }

    // The link itself, so it can be copied from the log.
    @Override
    public Option<String> subject() {
        return Option.of(url);
    }

    @Override
    public Msg run(Env env) {
        return ForgejoServices.browser(env).open(url).fold(this::failed, ran -> succeeded(List.of(ran)));
    }
}
