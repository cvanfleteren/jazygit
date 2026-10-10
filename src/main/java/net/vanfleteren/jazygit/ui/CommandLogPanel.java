package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.style.Color;
import dev.tamboui.style.Overflow;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Panel;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.LogEntry;
import net.vanfleteren.jazygit.state.Model;

import java.util.ArrayList;
import java.util.List;

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

    private CommandLogPanel() {
    }

    public static Panel render(Model model) {
        List<Element> lines = new ArrayList<>();
        for (LogEntry entry : model.commandLog()) {
            lines.add(text(entry.title()).bold());
            entry.commands().forEach(command -> lines.add(text(INDENT + command).dim()));
        }
        // The error may wrap, so it is kept out of the tail cut and always shown.
        int room = Math.max(0, VISIBLE_LINES - (model.error().isPresent() ? 1 : 0));
        List<Element> shown = new ArrayList<>(lines.subList(Math.max(0, lines.size() - room), lines.size()));
        model.error().ifPresent(error -> shown.add(text(error).red().overflow(Overflow.WRAP_WORD)));
        return panel(Messages.get("panel.commandLog.title"), shown.toArray(Element[]::new))
                .rounded()
                .borderColor(Color.WHITE);
    }
}
