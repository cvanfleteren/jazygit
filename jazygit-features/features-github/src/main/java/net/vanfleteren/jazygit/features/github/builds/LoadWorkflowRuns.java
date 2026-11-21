package net.vanfleteren.jazygit.features.github.builds;

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
 * (ADR 131). It runs in the background, as it talks to GitHub.
 */
public record LoadWorkflowRuns(String workflowId, Option<String> after) implements Cmd {

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
                .fold(this::failed, page -> new BuildsMsg.RunsRead(workflowId, after, page));
    }

    @Override
    public Msg failed(Failure failure) {
        return new BuildsMsg.RunsFailed(workflowId, after, failure);
    }
}
