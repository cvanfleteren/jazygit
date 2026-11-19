package net.vanfleteren.jazygit.kernel.state.load;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.Worktree;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Repo;

public record LoadWorktrees() implements Targeted<List<Worktree>> {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("state.work.worktrees"));
    }

    @Override
    public Target<List<Worktree>> target() {
        return Target.inRepo(Repo::worktrees, Repo::withWorktrees);
    }

    @Override
    public Msg run(Env env) {
        return env.git().worktrees().list().mapLeft(Failure::of).fold(this::failed, LoadMsg.WorktreesLoaded::new);
    }
}
