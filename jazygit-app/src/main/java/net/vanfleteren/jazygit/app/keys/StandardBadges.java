package net.vanfleteren.jazygit.app.keys;

import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchBadges;

/**
 * What the branches pane shows of a branch beside what git says: the badges of the features, in the order of
 * {@link Features#ALL} (ADR 167). It is the {@link BranchBadges#standard()} badges, given to the pane as a service, as
 * the keymap is (ADR 136).
 */
public final class StandardBadges implements BranchBadges.Provider {

    private static final BranchBadges BADGES = new BranchBadges(Features.ALL.flatMap(Feature::badges));

    @Override
    public BranchBadges badges() {
        return BADGES;
    }
}
