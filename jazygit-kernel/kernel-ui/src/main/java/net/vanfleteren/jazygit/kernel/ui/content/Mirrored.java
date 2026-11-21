package net.vanfleteren.jazygit.kernel.ui.content;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.preferences.Screen;

/**
 * What the content panel shows the highlight of: one of the left panes of the repository screen, or on a screen a
 * feature brings, whichever of its panes is in use (ADR 168). An area is resolved to its pane by
 * {@link ContentArea#pane}.
 */
public sealed interface Mirrored {

    Mirrored FILES = new Pane(PaneId.FILES);
    Mirrored BRANCHES = new Pane(PaneId.BRANCHES);
    Mirrored COMMITS = new Pane(PaneId.COMMITS);
    Mirrored STASH = new Pane(PaneId.STASH);

    /**
     * The panes of the repository screen the content panel mirrors, top to bottom.
     */
    List<Mirrored> REPOSITORY = List.of(FILES, BRANCHES, COMMITS, STASH);

    /**
     * The pane of the repository screen that is {@code pane}, if the content panel mirrors it.
     */
    static Option<Mirrored> of(PaneId pane) {
        return REPOSITORY.find(mirrored -> mirrored.equals(new Pane(pane)));
    }

    /**
     * The panes of {@code screen}, a screen a feature brings, which the content panel mirrors whatever has the focus.
     */
    static Mirrored of(Screen screen) {
        return new Panes(screen);
    }

    /**
     * A pane of the repository screen.
     *
     * @param pane its id
     */
    record Pane(PaneId pane) implements Mirrored {}

    /**
     * The panes of a screen a feature brings: the content panel shows the highlight of the one in use.
     *
     * @param screen the screen
     */
    record Panes(Screen screen) implements Mirrored {}
}
