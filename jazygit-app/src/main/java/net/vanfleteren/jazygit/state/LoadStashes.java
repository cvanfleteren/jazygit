package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Stash;

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
