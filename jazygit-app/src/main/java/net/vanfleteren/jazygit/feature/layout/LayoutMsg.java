package net.vanfleteren.jazygit.feature.layout;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Screen;
import net.vanfleteren.jazygit.state.Update;

public sealed interface LayoutMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return LayoutUpdate.update(model, this);
    }

    /**
     * The user asked for the left column of the screen shown to be {@code by} percent of the width wider, or
     * narrower when it is negative.
     */
    record Stepped(int by) implements LayoutMsg {}

    /**
     * The user dragged the border of the left column of {@code screen} to {@code width} percent of the width.
     */
    record Resized(Screen screen, int width) implements LayoutMsg {}

    /**
     * The user let go of the border of the left column of {@code screen}.
     */
    record Settled(Screen screen) implements LayoutMsg {}

    /**
     * The user dragged the bottom border of pane {@code pane} of the left column of {@code screen}, counted from the
     * first under the status, to {@code bottom} percent of the height of the column below the top of that first pane.
     */
    record PaneResized(Screen screen, int pane, int bottom) implements LayoutMsg {}

    /**
     * The user let go of the bottom border of a pane of the left column of {@code screen}.
     */
    record PaneSettled(Screen screen) implements LayoutMsg {}
}
