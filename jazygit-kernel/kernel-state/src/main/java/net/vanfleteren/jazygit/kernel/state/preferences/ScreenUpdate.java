package net.vanfleteren.jazygit.kernel.state.preferences;

import net.vanfleteren.jazygit.kernel.state.Events;
import net.vanfleteren.jazygit.kernel.state.KernelEvent;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Update.Next;

/**
 * Switching screens (ADR 168): the kernel shows the screen asked for, and tells the features it does, as what it
 * shows may be read again then; which screen it is is theirs to tell.
 */
public final class ScreenUpdate {

    private ScreenUpdate() {}

    public static Next update(Model model, ScreenMsg msg) {
        return switch (msg) {
            case ScreenMsg.Shown(Screen screen) -> shown(model, screen);
            case ScreenMsg.Toggled(Screen screen) ->
                shown(model, model.screen().equals(screen) ? Screen.REPOSITORY : screen);
        };
    }

    /**
     * {@code model} showing {@code screen}; the screen already shown stays as it is, without being read again. A
     * screen of a feature that is off is not shown (ADR 171).
     */
    private static Next shown(Model model, Screen screen) {
        return model.screen().equals(screen) || !screen.shown(model)
                ? Next.of(model)
                : Events.happened(Next.of(model.withScreen(screen)), new KernelEvent.ScreenShown(screen));
    }
}
