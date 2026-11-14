package net.vanfleteren.jazygit.feature.diff;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;

/**
 * Opening and closing the nodes of a version of JSON rendered (ADR 122).
 */
public sealed interface FoldMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return FoldUpdate.update(model, this);
    }

    /**
     * The user asked to close the node the cursor {@code at} is on if it is open, or to open it.
     */
    record Toggled(DiffCursorAt at) implements FoldMsg {}

    /**
     * The user asked to close every node of the version the cursor {@code at} is in while any is open, or to open
     * them all.
     */
    record AllToggled(DiffCursorAt at) implements FoldMsg {}
}
