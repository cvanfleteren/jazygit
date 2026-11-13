package net.vanfleteren.jazygit.state.github;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Reads how the CI checks are doing on the branches on GitHub, and which remotes are on GitHub. It runs in the
 * background, as it talks to GitHub, and nobody waits for it.
 */
public record LoadBranchChecks() implements Cmd {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("work.checks"));
    }

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        return env.gitHub()
                .checks().ofBranches()
                .mapLeft(Failure::of)
                .fold(
                        this::failed,
                        checks -> env.git()
                                .remotes()
                                .urls()
                                .mapLeft(Failure::of)
                                .fold(this::failed, urls -> new BranchChecksMsg.Loaded(GitHubChecks.of(checks, urls))));
    }

    @Override
    public Msg failed(Failure failure) {
        return new BranchChecksMsg.LoadFailed(failure);
    }
}
