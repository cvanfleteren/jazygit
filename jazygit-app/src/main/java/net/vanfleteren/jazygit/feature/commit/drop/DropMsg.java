package net.vanfleteren.jazygit.feature.commit.drop;

import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

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
