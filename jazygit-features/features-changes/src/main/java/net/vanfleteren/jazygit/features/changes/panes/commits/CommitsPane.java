package net.vanfleteren.jazygit.features.changes.panes.commits;

import net.vanfleteren.jazygit.core.repo.Sha;
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
    public static final Slot<PaneLine> LINE = Slot.of("changes.panes.commits.line");

    /**
     * What is listed in place of the log of HEAD.
     */
    public static final Slot<CommitsListing> LISTING = Slot.of("changes.panes.commits.listing");

    /**
     * What is listed above the log of HEAD, as the steps of an operation that stopped (ADR 199). Not above what a
     * feature lists in its place.
     */
    public static final Slot<CommitsAbove> ABOVE = Slot.of("changes.panes.commits.above");

    /**
     * The commits of the log of HEAD not pushed to the upstream of the branch yet, whose shas stand out (#722).
     */
    public static final Slot<Sha> UNPUSHED = Slot.of("changes.panes.commits.unpushed");

    /**
     * The tabs the features add after the log, in the order of the features (ADR 185).
     */
    public static final Slot<PaneTab<?>> TABS = Slot.of("changes.panes.commits.tabs");

    /**
     * The pane's own first tab: the log of HEAD, or what is listed in its place.
     */
    public static final TabId LOG = new TabId("changes.panes.commits.log");

    private CommitsPane() {}
}
