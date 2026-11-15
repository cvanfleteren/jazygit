package net.vanfleteren.jazygit.state.load;

import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Reads whether each of {@code worktrees} has changes, one after the other. One that cannot be read is left
 * out. It runs in the background, as it takes a git status per worktree, and nobody waits for it.
 */
public record LoadWorktreeChanges(List<String> worktrees) implements Load {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("work.worktreeChanges"));
    }

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        return new LoadMsg.WorktreeChangesLoaded(
                worktrees,
                worktrees
                        .flatMap(location -> env.git()
                                .worktrees()
                                .hasChanges(Path.of(location))
                                .toOption()
                                .map(changes -> Tuple.of(location, changes)))
                        .toMap(entry -> entry));
    }
}
