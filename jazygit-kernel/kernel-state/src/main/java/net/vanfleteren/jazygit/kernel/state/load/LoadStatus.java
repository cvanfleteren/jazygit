package net.vanfleteren.jazygit.kernel.state.load;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.RepoStatus;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Repo;

public record LoadStatus() implements Targeted<RepoStatus> {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("state.work.status"));
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
