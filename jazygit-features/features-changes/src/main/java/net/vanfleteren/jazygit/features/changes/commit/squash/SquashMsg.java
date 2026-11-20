package net.vanfleteren.jazygit.features.changes.commit.squash;

import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

public sealed interface SquashMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return SquashUpdate.update(model, this);
    }

    /**
     * The user asked to squash the commit {@code sha} into the one below it.
     */
    record Requested(Sha sha) implements SquashMsg {}
}
