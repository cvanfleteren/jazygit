package net.vanfleteren.jazygit.features.github.browse;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.PullRequestLink;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * Opens the pull request {@code link} is to in the browser, on the review comment it names, if any (ADR 85).
 */
public record BrowseRemarkCmd(PullRequestLink link) implements OperationCmd {

    @Override
    public Operation operation() {
        return BrowsePullRequestCmd.OPEN_PR;
    }

    // The link itself, so it can be copied from the log.
    @Override
    public Option<String> subject() {
        return Option.of(link.url());
    }

    @Override
    public Msg run(Env env) {
        return env.gitHub()
                .account()
                .openInBrowser(link)
                .mapLeft(GitHubFailure::of)
                .fold(this::failed, this::succeeded);
    }
}
