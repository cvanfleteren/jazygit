package net.vanfleteren.jazygit.feature.copy;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.state.operation.Notice;

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
