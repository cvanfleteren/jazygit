package net.vanfleteren.jazygit.features.changes.copy;

import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.kernel.state.operation.Notice;

public sealed interface CopyMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return CopyUpdate.update(model, this);
    }

    /**
     * The user asked to copy what the cursor {@code at} is on (ADR 94).
     */
    record Asked(DiffCursorAt at) implements CopyMsg {}

    /**
     * The terminal was asked to copy, or could not be; {@code notice} tells the user which.
     */
    record Done(Notice notice) implements CopyMsg {}
}
