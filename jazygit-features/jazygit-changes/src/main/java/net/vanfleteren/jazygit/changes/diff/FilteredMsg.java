package net.vanfleteren.jazygit.changes.diff;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;

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
     * The user asked to hide again the hunk opened that the line the cursor {@code at} is on is of, if it is of one.
     */
    record Closed(DiffCursorAt at) implements FilteredMsg {}
}
