package net.vanfleteren.jazygit.features.changes.commit.revert;

import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

public sealed interface RevertMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return RevertUpdate.update(model, this);
    }

    /**
     * The user asked to revert the commit {@code sha}.
     */
    record Requested(Sha sha) implements RevertMsg {}
}
