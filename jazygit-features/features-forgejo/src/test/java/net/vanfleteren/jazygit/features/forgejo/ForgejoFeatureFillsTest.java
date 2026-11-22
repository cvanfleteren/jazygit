package net.vanfleteren.jazygit.features.forgejo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.vanfleteren.jazygit.features.branches.panes.branches.BranchBadges;
import net.vanfleteren.jazygit.features.branches.panes.branches.BranchGroups;
import net.vanfleteren.jazygit.kernel.state.FeatureUpdate;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.ui.Fills;
import org.junit.jupiter.api.Test;

/**
 * What Forgejo gives the branches pane (ADR 172, 174): the badge of the last build of a branch while it is on for the
 * repository, nothing while it is off, and no groups.
 */
class ForgejoFeatureFillsTest {

    private static final Fills FILLS = ForgejoFeature.FEATURE.gated().fills();
    private static final Model ON = TestModels.loaded();
    private static final Model OFF = FeatureUpdate.off(ON, ForgejoFeature.ID);

    @Test
    void itBadgesTheBranchesWhileItIsOn() {
        assertTrue(FILLS.of(BranchBadges.KEY, ON).isDefined());
        assertFalse(FILLS.of(BranchBadges.KEY, OFF).isDefined());
    }

    @Test
    void itGroupsNoBranches() {
        assertFalse(FILLS.of(BranchGroups.KEY, ON).isDefined());
    }
}
