package net.vanfleteren.jazygit.state.github.builds;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Reads the workflows of the repository, the tabs of the builds on every branch (ADR 131). It runs in the background,
 * as it talks to GitHub.
 */
public record LoadRepoWorkflows() implements Cmd {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("state.work.repoWorkflows"));
    }

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        return env.gitHub()
                .checks()
                .workflows()
                .mapLeft(Failure::of)
                .fold(this::failed, BuildsViewMsg.RepoWorkflowsRead::new);
    }

    @Override
    public Msg failed(Failure failure) {
        return new BuildsViewMsg.RepoWorkflowsFailed(failure);
    }
}
