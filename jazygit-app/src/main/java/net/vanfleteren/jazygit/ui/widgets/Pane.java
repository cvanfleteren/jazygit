package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.panel;

import dev.tamboui.layout.Alignment;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Panel;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.ui.Theme;

/**
 * The border around a left-side pane: its title on top and, once there are rows, the position of
 * the selected row ("1/2") at the bottom right.
 */
public final class Pane {

    private Pane() {}

    /**
     * @param selected the index of the selected row
     * @param count    the number of rows, or empty to show no position, as long as they are not known
     */
    public static Panel bordered(String title, Element content, boolean focused, int selected, Option<Integer> count) {
        return bordered(title(title), content, focused, selected, count);
    }

    /**
     * @param title    the title, in styles of its own
     * @param selected the index of the selected row
     * @param count    the number of rows, or empty to show no position, as long as they are not known
     */
    public static Panel bordered(Line title, Element content, boolean focused, int selected, Option<Integer> count) {
        Panel panel = panel(content).title(title);
        if (focused) {
            panel.addClass(Theme.Classes.FOCUSED);
        }
        count.forEach(total ->
                panel.bottomTitle((total == 0 ? 0 : selected + 1) + "/" + total).bottomTitleAlignment(Alignment.RIGHT));
        return panel;
    }

    /**
     * A plain title, in bold like the name of the shown list in a title with more than one.
     */
    public static Line title(String title) {
        return Line.from(Span.raw(title).bold());
    }
}
