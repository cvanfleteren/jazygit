package net.vanfleteren.jazygit.features.changes.reflog;

import net.vanfleteren.jazygit.features.changes.panes.commits.CommitsPane;
import net.vanfleteren.jazygit.features.changes.reflog.content.ReflogShown;
import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * The reflog of HEAD, read only, in a tab of the commits pane after the log (ADR 186). The keys on an entry are bound
 * in {@link Scopes#REFLOG} by the features they belong to: its files by the diff, copying its sha by the copy.
 */
public final class ReflogFeature {

    /**
     * Its tab, which hosts the keys that switch the tabs of the pane; what it reads, while it is shown; and what its
     * mark means.
     */
    public static final Feature FEATURE = Feature.of(
                    FeatureAction.ofKeys(keys -> keys.host(Scopes.REFLOG, Scopes.PANE_TABS)))
            .filling(CommitsPane.TABS, ReflogShown::tab)
            .reacting(ReflogUpdate::reacting)
            .explaining(ReflogShown.HEAD_LEGEND);

    private ReflogFeature() {}
}
