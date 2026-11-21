package net.vanfleteren.jazygit.features.github.builds;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Reads the workflows of the repository, the tabs of the builds on every branch (ADR 131). It runs in the background,
 * as it talks to GitHub.
 */
public record LoadRepoWorkflows() implements Cmd {

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
        return env.gitHub()
                .checks()
                .workflows()
                .mapLeft(GitHubFailure::of)
                .fold(this::failed, BuildsMsg.RepoWorkflowsRead::new);
    }

    @Override
    public Msg failed(Failure failure) {
        return new BuildsMsg.RepoWorkflowsFailed(failure);
    }
}
