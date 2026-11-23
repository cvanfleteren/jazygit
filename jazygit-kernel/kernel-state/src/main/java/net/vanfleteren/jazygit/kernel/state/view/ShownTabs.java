package net.vanfleteren.jazygit.kernel.state.view;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.kernel.state.PaneId;

/**
 * The tabs a pane shows, which the keys that switch between them act on (ADR 185).
 *
 * @param pane  the pane
 * @param tabs  its tabs, in the order they are shown; never empty
 * @param shown the one it shows, among them
 */
public record ShownTabs(PaneId pane, List<TabId> tabs, TabId shown) {

    /**
     * The message that shows the tab {@code by} further than the one shown, going round: -1 for the one before, 1 for
     * the one after.
     */
    public ViewMsg.TabChosen shifted(int by) {
        int at = Math.max(0, tabs.indexOf(shown));
        return new ViewMsg.TabChosen(pane, tabs.get(Math.floorMod(at + by, tabs.size())));
    }
}
