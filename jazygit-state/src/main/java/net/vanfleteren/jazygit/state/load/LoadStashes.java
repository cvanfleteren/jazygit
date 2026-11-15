package net.vanfleteren.jazygit.state.load;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Repo;

public record LoadStashes() implements Targeted<List<Stash>> {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("work.stashes"));
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
