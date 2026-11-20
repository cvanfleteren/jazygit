package net.vanfleteren.jazygit.kernel.state.preferences;

import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * Switching what the left of the window shows (ADR 168), whichever screen it is.
 */
public sealed interface ScreenMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return ScreenUpdate.update(model, this);
    }

    /**
     * The user asked to show {@code screen}, as by clicking its name.
     */
    record Shown(Screen screen) implements ScreenMsg {}

    /**
     * The user asked to show {@code screen}, or the repository when it already shows, as its owner's shortcut does.
     */
    record Toggled(Screen screen) implements ScreenMsg {}
}
