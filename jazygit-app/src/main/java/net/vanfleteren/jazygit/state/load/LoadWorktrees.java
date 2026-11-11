package net.vanfleteren.jazygit.state.load;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Worktree;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Repo;

public record LoadWorktrees() implements Targeted<List<Worktree>> {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("work.worktrees"));
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
