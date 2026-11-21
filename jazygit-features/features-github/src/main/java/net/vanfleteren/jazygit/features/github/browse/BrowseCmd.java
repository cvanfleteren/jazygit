package net.vanfleteren.jazygit.features.github.browse;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.features.github.GitHubServices;
import net.vanfleteren.jazygit.features.github.OnGitHub;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * Opens {@code branch} on GitHub, in the browser.
 */
public record BrowseCmd(RemoteBranch branch) implements OperationCmd {

    public static final Operation BROWSE = Operation.of("github.log.title.browse", "github.error.browse.failed");

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
        return GitHubServices.of(env)
                .account()
                .browse(OnGitHub.branch(branch))
                .mapLeft(GitHubFailure::of)
                .fold(this::failed, this::succeeded);
    }
}
