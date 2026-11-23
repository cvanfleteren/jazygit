package net.vanfleteren.jazygit.app.keys;

import net.vanfleteren.jazygit.features.changes.diff.DiffFilterSettings;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.diff.HunkFilters;

/**
 * The diff filters the kernel hides hunks with (ADR 184): those the diffs feature keeps. They are the
 * {@link HunkFilters#of(Model)} filters, given to the kernel as a service, as the content panel's sources are.
 */
public final class StandardFilters implements HunkFilters.Provider {

    @Override
    public HunkFilters filters(Model model) {
        return DiffFilterSettings.hiding(model);
    }
}
