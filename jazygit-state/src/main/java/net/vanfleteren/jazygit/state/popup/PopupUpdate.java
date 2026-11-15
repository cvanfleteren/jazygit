package net.vanfleteren.jazygit.state.popup;

import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.operation.OperationCmd;
import net.vanfleteren.jazygit.state.operation.OperationUpdate;

/**
 * The popups that ask about an operation: opening one, and starting the operation made of what was entered in it.
 */
public final class PopupUpdate {

    private PopupUpdate() {}

    /**
     * Opens {@code popup}, which asks about an operation to start: the error of the last one is not shown
     * anymore.
     */
    public static Next ask(Model model, Popup popup) {
        return Next.of(model.clearError().openPopup(popup));
    }

    /**
     * Opens {@code popup} as {@link #ask(Model, Popup)} does, if there is one: none when the operation it would
     * ask about cannot be done, and then nothing happens.
     */
    public static Next ask(Model model, Option<? extends Popup> popup) {
        return popup.map(open -> ask(model, open)).getOrElse(() -> Next.of(model));
    }

    /**
     * Confirms the open popup of type {@code type}: closes it, clears the error and starts the operation
     * {@code cmd} makes of it. When no such popup is open, or {@code cmd} makes none because what was entered
     * cannot be used, nothing happens.
     */
    public static <P extends Popup> Next confirm(
            Model model, Class<P> type, Function<? super P, Option<? extends OperationCmd>> cmd) {
        return model.popup(type)
                .flatMap(cmd)
                .map(operation -> OperationUpdate.start(model.withoutPopup(type).clearError(), operation))
                .getOrElse(() -> Next.of(model));
    }
}
