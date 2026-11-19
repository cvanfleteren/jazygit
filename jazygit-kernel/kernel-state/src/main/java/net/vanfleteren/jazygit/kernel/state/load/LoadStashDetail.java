package net.vanfleteren.jazygit.kernel.state.load;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.DiffOptions;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.git.model.Sha;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.view.Preview;
import net.vanfleteren.jazygit.kernel.state.view.PreviewView;
import net.vanfleteren.jazygit.kernel.state.view.RepoView;

public record LoadStashDetail(Sha sha, DiffOptions options) implements DiffLoad<DiffText> {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("state.work.stashDetail"));
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
        return Target.inView(
                RepoView.PREVIEW.andThen(Preview.lens(PreviewView::stashDetail, PreviewView::withStashDetail, sha)));
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
