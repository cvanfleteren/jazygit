package net.vanfleteren.jazygit.app.keys;

import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchGroups;

/**
 * The groups the branches pane makes of the local branches: those of the features, in the order of
 * {@link Features#ALL} (ADR 167). It is the {@link BranchGroups#standard()} groups, given to the pane as a service, as
 * the keymap is (ADR 136).
 */
public final class StandardGroups implements BranchGroups.Provider {

    private static final BranchGroups GROUPS = new BranchGroups(Features.ALL.flatMap(Feature::groupings));

    @Override
    public BranchGroups groups() {
        return GROUPS;
    }
}
