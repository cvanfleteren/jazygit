package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;

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
        return env.gitHub().protectedBranches().fold(this::failedWith, PullRequestsMsg.ProtectedLoaded::new);
    }

    @Override
    public Msg failed(Failure failure) {
        return new PullRequestsMsg.ProtectedLoadFailed(failure);
    }
}
