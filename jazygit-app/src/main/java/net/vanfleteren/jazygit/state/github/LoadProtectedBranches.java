package net.vanfleteren.jazygit.state.github;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Reads which branches are protected on GitHub, the roots that others are built on (ADR 72). It runs in the
 * background, as it talks to GitHub, and nobody waits for it.
 */
public record LoadProtectedBranches() implements Cmd {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("work.protectedBranches"));
    }

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        return env.gitHub()
                .checks().protectedBranches()
                .mapLeft(Failure::of)
                .fold(this::failed, PullRequestsMsg.ProtectedLoaded::new);
    }

    @Override
    public Msg failed(Failure failure) {
        return new PullRequestsMsg.ProtectedLoadFailed(failure);
    }
}
