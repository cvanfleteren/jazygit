package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.style.Overflow;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Panel;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.ui.text.ErrorText;
import net.vanfleteren.jazygit.ui.text.NoticeText;
import net.vanfleteren.jazygit.ui.theme.Theme;

/**
 * Right-hand panel under the content panel, logging the mutating git commands that were executed, each
 * under a title, followed by why the last operation failed, when it did, or by a notice about it. It is
 * display-only and does not take part in focus cycling. While the content panel is maximized, by collapsing the left
 * column (ADR 104), it shrinks to its borders and title, keeping the error or notice, so the content panel gets its
 * rows too.
 */
public final class CommandLogPanel {

    /**
     * How many lines fit in the panel; the oldest ones scroll off.
     */
    public static final int VISIBLE_LINES = 8;

    private static final String INDENT = "  ";

    /**
     * The rows the borders of the panel take.
     */
    private static final int BORDERS = 2;

    private CommandLogPanel() {}

    /**
     * How many rows the panel takes: its lines and borders, or while the content panel is maximized, its borders and
     * the error or notice alone.
     */
    public static int height(Model model) {
        return minimized(model) ? BORDERS + (outcome(model).isDefined() ? 1 : 0) : VISIBLE_LINES + BORDERS;
    }

    public static Panel render(Model model) {
        Option<Element> outcome = outcome(model);
        if (minimized(model)) {
            return panel(Messages.get("panel.commandLog.title"), outcome.toJavaArray(Element[]::new));
        }
        List<Element> lines = model.outcome()
                .commandLog()
                .flatMap(entry -> List.<Element>of(
                                text(Messages.get(entry.operation().logTitleKey()))
                                        .addClass(Theme.Classes.BOLD))
                        .appendAll(entry.commands()
                                .map(command -> text(INDENT + command.line()).addClass(Theme.Classes.DIM))));
        // The error or notice may wrap, so it is kept out of the tail cut and always shown.
        int room = Math.max(0, VISIBLE_LINES - (outcome.isDefined() ? 1 : 0));
        List<Element> shown = lines.takeRight(room).appendAll(outcome);
        return panel(Messages.get("panel.commandLog.title"), shown.toJavaArray(Element[]::new));
    }

    /**
     * Whether the content panel is maximized, which the left column of the screen shown collapses for (ADR 104).
     */
    private static boolean minimized(Model model) {
        return model.preferences().leftCollapsed(model.screen());
    }

    /**
     * Why the last operation failed, when it did, or else a notice about it.
     */
    private static Option<Element> outcome(Model model) {
        return model.outcome()
                .error()
                .map(error -> text(ErrorText.of(error)).addClass(Theme.Classes.ERROR))
                .orElse(() -> model.outcome()
                        .notice()
                        .map(notice -> text(NoticeText.of(notice)).addClass(Theme.Classes.NOTICE)))
                .map(line -> line.overflow(Overflow.WRAP_WORD));
    }
}
