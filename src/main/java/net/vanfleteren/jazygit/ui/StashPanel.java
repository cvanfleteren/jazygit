package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.elements.Panel;

/**
 * Bottom-left panel reserved for the stashes. It is a placeholder: it shows nothing yet and does not
 * take part in focus cycling.
 */
public final class StashPanel {

    private StashPanel() {
    }

    public static Panel render() {
        return Pane.bordered("Stash", text(""), false, 0, 0);
    }
}
