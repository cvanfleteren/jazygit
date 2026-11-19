package net.vanfleteren.jazygit.features.changes.commit.drop;

import net.vanfleteren.jazygit.core.git.model.Sha;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

public sealed interface DropMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return DropUpdate.update(model, this);
    }

    /**
     * The user asked to drop the commit {@code sha} from the history of the checked out branch.
     */
    record Requested(Sha sha) implements DropMsg {}
}
