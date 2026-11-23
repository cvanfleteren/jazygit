package net.vanfleteren.jazygit.features.changes.panes.commits;

import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Switching between the tabs of the commits pane (ADR 185): the log, and those the features add. The keys are those of
 * the tabs of any pane, which the log hosts; a feature's tab hosts them in its own scope.
 */
public final class CommitsTabsAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys -> keys.host(Scopes.COMMITS, Scopes.PANE_TABS)
            .add(
                    Scopes.PANE_TABS,
                    Binding.onSelected("[", "changes.help.tabs.previous", tabs -> tabs.shifted(-1)),
                    Binding.onSelected("]", "changes.help.tabs.next", tabs -> tabs.shifted(1))));

    private CommitsTabsAction() {}
}
