package net.vanfleteren.jazygit.kernel.ui.widgets;

import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;
import java.util.function.Function;

/**
 * The keys every field of a form takes, as those of the dialogs that create and edit a pull request (ADR 18, 59):
 * Escape cancels and Ctrl+S submits, from any field; the form's own keys, as Ctrl+O to open the body in the editor,
 * come after those. The dialog around the fields only gets keys after the global handlers, and those take Escape and
 * Ctrl+S too, so each field takes them itself.
 *
 * @param cancel what Escape does
 * @param submit what Ctrl+S does, and Enter in a {@link TitleField}
 * @param others the form's own keys, for those neither takes
 */
public record FormKeys(Runnable cancel, Runnable submit, Function<KeyEvent, EventResult> others) {

    // With Ctrl held, submits the form from any field; Enter adds a line to a body.
    private static final char SUBMIT_KEY = 's';

    /**
     * The keys of a form without keys of its own.
     */
    public FormKeys(Runnable cancel, Runnable submit) {
        this(cancel, submit, event -> EventResult.UNHANDLED);
    }

    /**
     * Cancels on Escape and submits on Ctrl+S; hands the other keys to the form's own.
     */
    public EventResult handle(KeyEvent event) {
        if (event.isCancel()) {
            cancel.run();
            return EventResult.HANDLED;
        }
        if (event.hasCtrl() && event.isChar(SUBMIT_KEY)) {
            submit.run();
            return EventResult.HANDLED;
        }
        return others.apply(event);
    }
}
