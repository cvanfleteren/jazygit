package net.vanfleteren.jazygit.feature.worktree.switchto;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * Switching to another worktree, which the app then shows as if it had been started there.
 */
public sealed interface SwitchWorktreeMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return SwitchWorktreeUpdate.update(model, this);
    }

    /**
     * The user asked to show the worktree at {@code location} instead of this one.
     */
    record Requested(String location) implements SwitchWorktreeMsg {}
}
