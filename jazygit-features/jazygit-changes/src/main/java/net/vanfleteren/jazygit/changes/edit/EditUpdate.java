package net.vanfleteren.jazygit.changes.edit;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.operation.OperationUpdate;

/**
 * Opening files in the editor.
 */
public final class EditUpdate {

    private EditUpdate() {}

    public static Next update(Model model, EditMsg msg) {
        return switch (msg) {
            case EditMsg.Requested(String path) ->
                OperationUpdate.start(model.clearError(), new EditCmd(List.of(path)));
        };
    }
}
