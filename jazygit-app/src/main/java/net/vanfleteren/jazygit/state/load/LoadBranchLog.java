package net.vanfleteren.jazygit.state.load;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.view.Preview;
import net.vanfleteren.jazygit.state.view.RepoView;

/**
 * Reads the log of {@code branch}, and then what of it is not on its base, as {@code ahead} does: the log is shown
 * with it.
 */
public record LoadBranchLog(BranchRef branch, LoadAhead ahead) implements Targeted<List<Commit>> {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("work.branchLog", branch.name()));
    }

    @Override
    public Option<Slot> slot() {
        return Option.of(Slot.BRANCH_LOG);
    }

    /**
     * The log of the branch, while it is the one highlighted.
     */
    @Override
    public Target<List<Commit>> target() {
        return Target.inView(Preview.lens(RepoView::branchLog, RepoView::withBranchLog, branch));
    }

    @Override
    public Msg run(Env env) {
        return env.git()
                .history()
                .log(branch)
                .mapLeft(Failure::of)
                .fold(this::failed, commits -> new LoadMsg.BranchLogLoaded(branch, commits, ahead, ahead.read(env)));
    }
}
