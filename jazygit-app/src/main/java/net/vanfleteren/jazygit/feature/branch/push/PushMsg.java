package net.vanfleteren.jazygit.feature.branch.push;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface PushMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return PushUpdate.update(model, this);
    }

    /**
     * The user asked to push a branch.
     */
    record Requested(Branch.Name branch) implements PushMsg {}

    record ForceCancelled() implements PushMsg {}

    record UpstreamCancelled() implements PushMsg {}

    /**
     * The user confirmed the upstream typed for a branch without one, which should be {@code <remote> <branch>}.
     */
    record UpstreamConfirmed() implements PushMsg {}

    /**
     * The user confirmed overwriting the diverged remote branch.
     */
    record ForceConfirmed() implements PushMsg {}
}
