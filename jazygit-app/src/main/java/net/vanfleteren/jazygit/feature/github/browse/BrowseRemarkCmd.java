package net.vanfleteren.jazygit.feature.github.browse;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequestLink;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.operation.Operation;
import net.vanfleteren.jazygit.state.operation.OperationCmd;

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
        return env.gitHub().openInBrowser(link).mapLeft(Failure::of).fold(this::failed, this::succeeded);
    }
}
