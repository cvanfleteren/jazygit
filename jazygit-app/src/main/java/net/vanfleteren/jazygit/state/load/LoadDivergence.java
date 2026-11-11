package net.vanfleteren.jazygit.state.load;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Divergence;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Lens;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.view.DivergenceView;
import net.vanfleteren.jazygit.state.view.RepoView;

/**
 * Reads the commits only on {@code branch} and the ones only on {@code other}.
 */
public record LoadDivergence(BranchName branch, BranchRef other) implements Targeted<Divergence> {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("work.divergence", branch.value(), other.name()));
    }

    /**
     * The commits of the divergence, while it is the one open.
     */
    @Override
    public Target<Divergence> target() {
        return Target.inView(Lens.where(
                        RepoView::divergence,
                        RepoView::withDivergence,
                        view -> view.load().equals(this))
                .andThen(Lens.of(DivergenceView::commits, DivergenceView::withCommits)));
    }

    @Override
    public Msg run(Env env) {
        return env.git()
                .history()
                .divergence(new BranchRef.Local(branch), other)
                .mapLeft(Failure::of)
                .fold(this::failed, divergence -> new LoadMsg.DivergenceLoaded(branch, other, divergence));
    }
}
