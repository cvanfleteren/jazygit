package net.vanfleteren.jazygit.feature.diff;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;

public sealed interface ExpandMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return ExpandUpdate.update(model, this);
    }

    /**
     * The user asked to show lines the marker the cursor {@code at} is on leaves out, as {@code toward} says
     * (ADR 77).
     */
    record Asked(DiffCursorAt at, Toward toward) implements ExpandMsg {}

    /**
     * Which of the lines a marker leaves out are shown.
     */
    enum Toward {
        /**
         * The next ones below the hunk above it.
         */
        DOWN,
        /**
         * The ones just above the hunk below it.
         */
        UP,
        /**
         * All of them.
         */
        ALL
    }
}
