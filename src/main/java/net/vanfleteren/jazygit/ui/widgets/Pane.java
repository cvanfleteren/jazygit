package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.layout.Alignment;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Panel;
import java.util.OptionalInt;
import net.vanfleteren.jazygit.ui.Theme;

/**
 * The border around a left-side pane: its title on top and, once there are rows, the position of
 * the selected row ("1/2") at the bottom right.
 */
public final class Pane {

    private Pane() {
    }

    /**
     * @param selected the index of the selected row
     * @param count    the number of rows, or empty to show no position, as long as they are not known
     */
    public static Panel bordered(String title, Element content, boolean focused, int selected, OptionalInt count) {
        Panel panel = panel(title, content);
        if (focused) {
            panel.addClass(Theme.Classes.FOCUSED);
        }
        if (count.isPresent()) {
            int total = count.getAsInt();
            panel.bottomTitle((total == 0 ? 0 : selected + 1) + "/" + total)
                    .bottomTitleAlignment(Alignment.RIGHT);
        }
        return panel;
    }
}
