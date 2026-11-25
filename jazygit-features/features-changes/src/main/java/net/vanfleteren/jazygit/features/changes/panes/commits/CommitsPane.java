package net.vanfleteren.jazygit.features.changes.panes.commits;

import net.vanfleteren.jazygit.kernel.state.view.TabId;
import net.vanfleteren.jazygit.kernel.ui.Slot;
import net.vanfleteren.jazygit.kernel.ui.tabs.PaneTab;
import net.vanfleteren.jazygit.kernel.ui.widgets.PaneLine;

/**
 * What the features can fill in the commits pane (ADR 172), which knows no feature. For the line and the listing, the
 * first feature that gives anything, in the order of the features, is the one shown; every tab given is shown.
 */
public final class CommitsPane {

    /**
     * The line above the list to type in, while it is open.
     */
    public static final Slot<PaneLine> LINE = Slot.of("changes.panes.commits.line", PaneLine.class);

    /**
     * What is listed in place of the log of HEAD.
     */
    public static final Slot<CommitsListing> LISTING = Slot.of("changes.panes.commits.listing", CommitsListing.class);

    /**
     * What is listed above the log of HEAD, as the steps of an operation that stopped (ADR 199). Not above what a
     * feature lists in its place.
     */
    public static final Slot<CommitsAbove> ABOVE = Slot.of("changes.panes.commits.above", CommitsAbove.class);

    /**
     * The tabs the features add after the log, in the order of the features (ADR 185).
     */
    @SuppressWarnings("unchecked")
    public static final Slot<PaneTab<?>> TABS =
            Slot.of("changes.panes.commits.tabs", (Class<PaneTab<?>>) (Class<?>) PaneTab.class);

    /**
     * The pane's own first tab: the log of HEAD, or what is listed in its place.
     */
    public static final TabId LOG = new TabId("changes.panes.commits.log");

    private CommitsPane() {}
}
