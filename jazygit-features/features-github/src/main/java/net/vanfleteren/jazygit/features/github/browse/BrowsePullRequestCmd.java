package net.vanfleteren.jazygit.features.github.browse;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.features.github.GitHubServices;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * Opens {@code pullRequest}, of any repository, in the browser.
 */
public record BrowsePullRequestCmd(PullRequestSummary pullRequest) implements OperationCmd {

    /**
     * Opening a pull request in the browser: one listed on the pull requests screen, or the one shown.
     */
    public static final Operation OPEN_PR = Operation.of("github.log.title.openPr", "github.error.openPr.failed");

    @Override
    public Operation operation() {
        return OPEN_PR;
    }

    @Override
    public Option<String> subject() {
        return Option.of(
                pullRequest.repository().value() + "#" + pullRequest.number().value());
    }

    @Override
    public Msg run(Env env) {
        return GitHubServices.of(env)
                .account()
                .openPullRequest(pullRequest.url())
                .mapLeft(GitHubFailure::of)
                .fold(this::failed, this::succeeded);
    }
}
