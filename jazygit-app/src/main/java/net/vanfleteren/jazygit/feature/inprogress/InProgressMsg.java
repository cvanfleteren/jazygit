package net.vanfleteren.jazygit.feature.inprogress;

import net.vanfleteren.jazygit.git.model.InProgressAction;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

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

    /**
     * The user confirmed aborting or skipping.
     */
    record Confirmed() implements InProgressMsg {}
}
