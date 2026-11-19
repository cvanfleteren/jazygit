package net.vanfleteren.jazygit.features.changes.diff;

import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.diff.DiffCursorAt;

/**
 * Opening and closing the hunks the diff filters hide (ADR 144).
 */
public sealed interface FilteredMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return FilteredUpdate.update(model, this);
    }

    /**
     * The user asked to show the hunks of the marker the cursor {@code at} is on.
     */
    record Opened(DiffCursorAt at) implements FilteredMsg {}

    /**
     * The user asked to hide again the hunks opened of the marker the cursor {@code at} is on.
     */
    record Closed(DiffCursorAt at) implements FilteredMsg {}
}
