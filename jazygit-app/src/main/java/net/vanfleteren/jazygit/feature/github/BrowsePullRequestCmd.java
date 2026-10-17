package net.vanfleteren.jazygit.feature.github;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.OperationCmd;

/**
 * Opens {@code pullRequest}, of any repository, in the browser.
 */
public record BrowsePullRequestCmd(PullRequestSummary pullRequest) implements OperationCmd {

    @Override
    public Operation operation() {
        return PullRequestCmd.OPEN_PR;
    }

    @Override
    public Option<String> subject() {
        return Option.of(pullRequest.repository() + "#" + pullRequest.number());
    }

    @Override
    public Msg run(Env env) {
        return env.gitHub().openPullRequest(pullRequest.url()).fold(this::failedWith, this::succeeded);
    }
}
