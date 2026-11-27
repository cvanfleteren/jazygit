package net.vanfleteren.jazygit.features.github.builds.screen;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.features.github.GitHubServices;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Reads runs of the workflow {@code workflowId} on every branch, from {@code after} on, or from the newest without
 * (ADR 212); {@code refresh} when it reads the newest again. It runs in the background, as it talks to GitHub.
 */
public record LoadScreenRuns(String workflowId, Option<String> after, boolean refresh) implements Cmd {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("github.work.workflowRuns"));
    }

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        return GitHubServices.of(env)
                .checks()
                .workflowRuns(workflowId, after)
                .mapLeft(GitHubFailure::of)
                .fold(this::failed, page -> new BuildsScreenMsg.RunsRead(workflowId, after, refresh, page));
    }

    @Override
    public Msg failed(Failure failure) {
        return new BuildsScreenMsg.RunsFailed(workflowId, after, refresh, failure);
    }
}
