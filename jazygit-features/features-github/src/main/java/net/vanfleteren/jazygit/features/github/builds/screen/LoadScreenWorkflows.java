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
 * Reads the workflows of the repository, which the builds screen lists (ADR 212). It runs in the background, as it
 * talks to GitHub.
 */
public record LoadScreenWorkflows() implements Cmd {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("github.work.repoWorkflows"));
    }

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        return GitHubServices.of(env)
                .checks()
                .workflows()
                .mapLeft(GitHubFailure::of)
                .fold(this::failed, BuildsScreenMsg.WorkflowsRead::new);
    }

    @Override
    public Msg failed(Failure failure) {
        return new BuildsScreenMsg.WorkflowsFailed(failure);
    }
}
