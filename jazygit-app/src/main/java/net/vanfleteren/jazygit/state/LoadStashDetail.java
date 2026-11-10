package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Sha;

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
