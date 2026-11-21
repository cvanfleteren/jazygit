package net.vanfleteren.jazygit.features.changes.panes.commits;

import net.vanfleteren.jazygit.kernel.ui.PaneKey;
import net.vanfleteren.jazygit.kernel.ui.widgets.PaneLine;

/**
 * What the features can fill in the commits pane (ADR 172), which knows no feature. For each key, the first feature
 * that gives anything, in the order of the features, is the one shown.
 */
public final class CommitsPane {

    /**
     * The line above the list to type in, while it is open.
     */
    public static final PaneKey<PaneLine> LINE = PaneKey.of("changes.panes.commits.line", PaneLine.class);

    /**
     * What is listed in place of the log of HEAD.
     */
    public static final PaneKey<CommitsListing> LISTING =
            PaneKey.of("changes.panes.commits.listing", CommitsListing.class);

    private CommitsPane() {}
}
