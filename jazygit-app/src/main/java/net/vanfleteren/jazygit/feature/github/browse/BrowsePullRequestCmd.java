package net.vanfleteren.jazygit.feature.github.browse;

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

    /**
     * Opening a pull request in the browser: one listed on the pull requests screen, or the one shown.
     */
    public static final Operation OPEN_PR = Operation.of("log.title.openPr", "error.openPr.failed");

    @Override
    public Operation operation() {
        return OPEN_PR;
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
