package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.style.Overflow;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Panel;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;

/**
 * Right-hand panel under the content panel, logging the mutating git commands that were executed, each
 * under a title, followed by why the last operation failed, when it did. It is display-only and does not
 * take part in focus cycling.
 */
public final class CommandLogPanel {

    /**
     * How many lines fit in the panel; the oldest ones scroll off.
     */
    public static final int VISIBLE_LINES = 8;

    private static final String INDENT = "  ";

    private CommandLogPanel() {}

    public static Panel render(Model model) {
        List<Element> lines = model.commandLog()
                .flatMap(entry -> List.<Element>of(
                                text(Messages.get(entry.operation().logTitleKey()))
                                        .addClass(Theme.Classes.BOLD))
                        .appendAll(entry.commands()
                                .map(command -> text(INDENT + command.line()).addClass(Theme.Classes.DIM))));
        // The error may wrap, so it is kept out of the tail cut and always shown.
        int room = Math.max(0, VISIBLE_LINES - (model.error().isDefined() ? 1 : 0));
        List<Element> shown = lines.takeRight(room)
                .appendAll(model.error()
                        .map(error -> text(ErrorText.of(error))
                                .addClass(Theme.Classes.ERROR)
                                .overflow(Overflow.WRAP_WORD)));
        return panel(Messages.get("panel.commandLog.title"), shown.toJavaArray(Element[]::new));
    }
}
