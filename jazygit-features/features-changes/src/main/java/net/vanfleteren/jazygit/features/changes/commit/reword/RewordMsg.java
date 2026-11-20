package net.vanfleteren.jazygit.features.changes.commit.reword;

import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

public sealed interface RewordMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return RewordUpdate.update(model, this);
    }

    /**
     * The user asked to reword the commit {@code sha}; any commit can be.
     */
    record Requested(Sha sha) implements RewordMsg {}
}
