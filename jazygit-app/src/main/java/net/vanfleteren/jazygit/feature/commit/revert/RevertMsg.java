package net.vanfleteren.jazygit.feature.commit.revert;

import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface RevertMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return RevertUpdate.update(model, this);
    }

    /**
     * The user asked to revert the commit {@code sha}.
     */
    record Requested(Sha sha) implements RevertMsg {}

    record Cancelled() implements RevertMsg {}

    record Confirmed() implements RevertMsg {}
}
