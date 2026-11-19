package net.vanfleteren.jazygit.kernel.state.load;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.Stash;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Repo;

public record LoadStashes() implements Targeted<List<Stash>> {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("state.work.stashes"));
    }

    @Override
    public Target<List<Stash>> target() {
        return Target.inRepo(Repo::stashes, Repo::withStashes);
    }

    @Override
    public Msg run(Env env) {
        return env.git().stashes().list().mapLeft(Failure::of).fold(this::failed, LoadMsg.StashesLoaded::new);
    }
}
