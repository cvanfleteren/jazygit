package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.elements.TextElement;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Fetching;
import net.vanfleteren.jazygit.state.Model;

import static dev.tamboui.toolkit.Toolkit.text;

/**
 * The line under everything else saying what is being read in the background: the remote being fetched from,
 * or the pull requests being read from GitHub. It is always there, blank while nothing is read, so the panes
 * above it do not move.
 */
public final class FetchLine {

    /**
     * Rows of the line.
     */
    public static final int HEIGHT = 1;

    private FetchLine() {
    }

    public static TextElement render(Model model) {
        return text(model.fetching()
                .flatMap(Fetching::remote)
                .map(remote -> Messages.get("status.fetching", remote))
                .orElse(Option.when(model.readingPullRequests() > 0, () -> Messages.get("status.pullRequests")))
                .getOrElse(""))
                .addClass(Theme.Classes.DIM);
    }
}
