package net.vanfleteren.jazygit.feature.commit.squash;

import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

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
