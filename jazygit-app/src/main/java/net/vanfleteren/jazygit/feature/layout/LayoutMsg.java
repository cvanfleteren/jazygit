package net.vanfleteren.jazygit.feature.layout;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.preferences.Screen;

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

    /**
     * The user asked for the pane of the left column that had the focus last to be {@code by} percent of the height
     * of the column taller, or shorter when it is negative (ADR 92).
     */
    record PaneStepped(int by) implements LayoutMsg {}

    /**
     * The user asked for the pane of the left column, or the content panel, that had the focus last to be maximized,
     * or no longer to be if it is (ADR 92).
     */
    record MaximizeToggled() implements LayoutMsg {}

    /**
     * The user asked to see the left column of {@code screen} again, which is collapsed (ADR 104): by a click on it, or
     * by leaving the content panel for a pane in it.
     */
    record Expanded(Screen screen) implements LayoutMsg {}

    /**
     * The left column was drawn {@code rows} rows tall, which the steps of the heights of its panes go by (ADR 92).
     */
    record Measured(int rows) implements LayoutMsg {}
}
