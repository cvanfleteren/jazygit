package net.vanfleteren.jazygit.feature.edit;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface EditMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return EditUpdate.update(model, this);
    }

    /**
     * The user asked to open the file at {@code path}, relative to the working tree, in the editor.
     */
    record Requested(String path) implements EditMsg {}
}
