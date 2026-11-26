package net.vanfleteren.jazygit.kernel.state.view;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.PaneId;

/**
 * What an update asks the app to do with the view state it keeps and the model does not: where the focus is, and the
 * highlight of the files pane (ADR 2, 207). An update asks with {@link
 * net.vanfleteren.jazygit.kernel.state.Update.Next#asking}; the app does it once the message is handled, and, when it
 * moves the focus while a popup that is closing has it, once the popup is gone, as the popup gives the focus back to the
 * pane the model knows first.
 */
public sealed interface ViewIntent {

    /**
     * The command log takes the focus, to scroll through all it logged (ADR 129).
     */
    record ToCommandLog() implements ViewIntent {}

    /**
     * The pane {@code pane} takes the focus; only while {@code from} has it, if given, so a key in another pane that
     * sends the same message leaves the focus where it is.
     */
    record ToPane(PaneId pane, Option<PaneId> from) implements ViewIntent {

        /**
         * {@code pane} takes the focus, wherever it is.
         */
        public static ToPane to(PaneId pane) {
            return new ToPane(pane, Option.none());
        }

        /**
         * {@code pane} takes the focus while {@code from} has it.
         */
        public static ToPane from(PaneId from, PaneId pane) {
            return new ToPane(pane, Option.of(from));
        }
    }

    /**
     * The highlight of the files pane moves to its file {@code step} of the one highlighted, while the content it shows
     * has the focus, or had it before a popup took it (ADR 117).
     */
    record StepFile(FileStepMsg.Step step) implements ViewIntent {}
}
