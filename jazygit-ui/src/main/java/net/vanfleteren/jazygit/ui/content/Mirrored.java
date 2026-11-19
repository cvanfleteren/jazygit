package net.vanfleteren.jazygit.ui.content;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.PaneId;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * What the content panel shows the highlight of: one of the left panes of the repository screen, or on the pull
 * requests screen, whichever of its lists is in use. An area is resolved to its pane by {@link ContentArea#pane}.
 */
public enum Mirrored {
    FILES(Option.of(Scopes.FILES.pane())),
    BRANCHES(Option.of(Scopes.BRANCHES.pane())),
    COMMITS(Option.of(Scopes.COMMITS.pane())),
    STASH(Option.of(Scopes.STASH.pane())),
    /** The lists of the pull requests screen, which it mirrors whatever has the focus. */
    PULL_REQUESTS(Option.none());

    private final Option<PaneId> pane;

    Mirrored(Option<PaneId> pane) {
        this.pane = pane;
    }

    /**
     * The left pane of the repository screen that is {@code pane}, if the content panel mirrors it.
     */
    public static Option<Mirrored> of(PaneId pane) {
        return List.of(values()).find(mirrored -> mirrored.pane.contains(pane));
    }
}
