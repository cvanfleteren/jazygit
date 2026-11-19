package net.vanfleteren.jazygit.features.branches.worktree.add;

import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Asks git which commit {@code typed}, where the user wants a new worktree to start, names: what is typed is not
 * handed to git as a revision until git says what it is (ADR 40).
 */
public record ResolveStartCmd(String typed) implements Cmd {

    @Override
    public Msg run(Env env) {
        return env.git()
                .checkout()
                .resolve(typed)
                .mapLeft(Failure::of)
                .fold(this::failed, commit -> new AddWorktreeMsg.StartResolved(typed, commit));
    }

    @Override
    public Msg failed(Failure failure) {
        return new AddWorktreeMsg.StartNotResolved(typed, failure);
    }
}
