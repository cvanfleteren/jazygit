package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.elements.TextElement;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Fetching;
import net.vanfleteren.jazygit.state.Model;

/**
 * The line under everything else saying which commit is copied to cherry-pick, else what is being read in the
 * background: the remote being fetched from, or the pull requests being read from GitHub. It is always there,
 * blank while there is nothing to say, so the panes above it do not move.
 */
public final class FetchLine {

    /**
     * Rows of the line.
     */
    public static final int HEIGHT = 1;

    private FetchLine() {}

    public static TextElement render(Model model) {
        return model.cherryPick()
                .map(commit -> text(Messages.get("status.cherryPick", commit.shortSha())))
                .getOrElse(() -> background(model));
    }

    private static TextElement background(Model model) {
        return text(model.fetching()
                        .flatMap(Fetching::remote)
                        .map(remote -> Messages.get("status.fetching", remote))
                        .orElse(Option.when(model.readingPullRequests() > 0, () -> Messages.get("status.pullRequests")))
                        .getOrElse(""))
                .addClass(Theme.Classes.DIM);
    }
}
