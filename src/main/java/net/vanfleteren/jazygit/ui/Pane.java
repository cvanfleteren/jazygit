package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.layout.Alignment;
import dev.tamboui.style.Color;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Panel;

/**
 * The border around a left-side pane: its title on top and, once there are rows, the position of
 * the selected row ("1/2") at the bottom right.
 */
final class Pane {

    private Pane() {
    }

    /**
     * @param selected the index of the selected row
     * @param count    the number of rows, or 0 to show no position
     */
    static Panel bordered(String title, Element content, boolean focused, int selected, int count) {
        Panel panel = panel(title, content)
                .rounded()
                .borderColor(focused ? Color.CYAN : Color.WHITE);
        if (count > 0) {
            panel.bottomTitle((selected + 1) + "/" + count)
                    .bottomTitleAlignment(Alignment.RIGHT);
        }
        return panel;
    }
}
