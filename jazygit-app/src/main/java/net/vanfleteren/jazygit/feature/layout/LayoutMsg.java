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
}
