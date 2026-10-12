package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.elements.TextElement;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Fetching;
import net.vanfleteren.jazygit.state.Model;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * The line under everything else saying which remote is being fetched from. It is always there, blank
 * while nothing is fetched, so the panes above it do not move.
 */
public final class FetchLine {

    /** Rows of the line. */
    public static final int HEIGHT = 1;

    private FetchLine() {
    }

    public static TextElement render(Model model) {
        return text(model.fetching()
                .flatMap(Fetching::remote)
                .map(remote -> Messages.get("status.fetching", remote))
                .getOrElse(""))
                .addClass(Theme.Classes.DIM);
    }
}
