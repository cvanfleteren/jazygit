package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.RepoStatus;

public record LoadStatus() implements Targeted<RepoStatus> {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("work.status"));
    }

    @Override
    public Target<RepoStatus> target() {
        return Target.inRepo(Repo::status, Repo::withStatus);
    }

    @Override
    public Msg run(Env env) {
        return env.git().status().read().mapLeft(Failure::of).fold(this::failed, LoadMsg.StatusLoaded::new);
    }
}
