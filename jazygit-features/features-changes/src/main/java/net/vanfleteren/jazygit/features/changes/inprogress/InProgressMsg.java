package net.vanfleteren.jazygit.features.changes.inprogress;

import net.vanfleteren.jazygit.core.git.model.InProgressAction;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

public sealed interface InProgressMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return InProgressUpdate.update(model, this);
    }

    /**
     * The user asked what to do with the operation the repository is in the middle of.
     */
    record Requested() implements InProgressMsg {}

    /**
     * The user chose what to do with it.
     */
    record Chosen(InProgressAction action) implements InProgressMsg {}
}
