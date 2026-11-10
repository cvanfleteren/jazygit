package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Worktree;

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
