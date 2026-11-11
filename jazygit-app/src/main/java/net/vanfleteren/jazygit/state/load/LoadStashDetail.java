package net.vanfleteren.jazygit.state.load;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.view.Preview;
import net.vanfleteren.jazygit.state.view.RepoView;

public record LoadStashDetail(Sha sha, DiffOptions options) implements DiffLoad, Targeted<DiffText> {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("work.stashDetail"));
    }

    @Override
    public Option<Slot> slot() {
        return Option.of(Slot.STASH_DETAIL);
    }

    /**
     * The detail of the stash, while it is the one highlighted.
     */
    @Override
    public Target<DiffText> target() {
        return Target.inView(Preview.lens(RepoView::stashDetail, RepoView::withStashDetail, sha));
    }

    @Override
    public Msg run(Env env) {
        return env.git()
                .stashes()
                .changes(sha, options)
                .mapLeft(Failure::of)
                .fold(this::failed, changes -> new LoadMsg.StashDetailLoaded(sha, options, changes));
    }
}
