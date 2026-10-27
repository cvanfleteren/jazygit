package net.vanfleteren.jazygit.feature.commit.reset;

import net.vanfleteren.jazygit.git.model.ResetMode;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface ResetMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return ResetUpdate.update(model, this);
    }

    /**
     * The user asked to reset the checked out branch to the commit {@code sha}; how is still to be chosen.
     */
    record Requested(Sha sha) implements ResetMsg {}

    /**
     * The user chose how to reset.
     */
    record Chosen(ResetMode mode) implements ResetMsg {}

    /**
     * The user confirmed discarding the uncommitted changes with a hard reset.
     */
    record HardConfirmed() implements ResetMsg {}
}
