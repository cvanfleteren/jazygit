package net.vanfleteren.jazygit.feature.copy;

import net.vanfleteren.jazygit.state.DiffCursorAt;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Notice;
import net.vanfleteren.jazygit.state.Update;

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
