package net.vanfleteren.jazygit.feature.worktree.add;

import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Asks git which commit {@code typed}, where the user wants a new worktree to start, names: what is typed is not
 * handed to git as a revision until git says what it is (ADR 40).
 */
public record ResolveStartCmd(String typed) implements Cmd {

    @Override
    public Msg run(Env env) {
        return env.git()
                .resolve(typed)
                .fold(this::failedWith, commit -> new AddWorktreeMsg.StartResolved(typed, commit));
    }

    @Override
    public Msg failed(Failure failure) {
        return new AddWorktreeMsg.StartNotResolved(typed, failure);
    }
}
