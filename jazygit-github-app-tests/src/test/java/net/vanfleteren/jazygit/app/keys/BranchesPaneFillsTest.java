package net.vanfleteren.jazygit.app.keys;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.vanfleteren.jazygit.features.branches.panes.branches.BranchBadges;
import net.vanfleteren.jazygit.features.branches.panes.branches.BranchGroups;
import net.vanfleteren.jazygit.features.github.GitHubFeature;
import net.vanfleteren.jazygit.kernel.state.FeatureUpdate;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import org.junit.jupiter.api.Test;

/**
 * What the app's features give the branches pane (ADR 172): GitHub's badges and stacks while GitHub is on for the
 * repository, and nothing while it is off. These tests run without Forgejo, which fills the badges too.
 */
class BranchesPaneFillsTest {

    private static final Model ON = TestModels.loaded();
    private static final Model OFF = FeatureUpdate.off(ON, GitHubFeature.ID);

    @Test
    void gitHubBadgesTheBranchesWhileItIsOn() {
        assertTrue(Features.FILLS.of(BranchBadges.KEY, ON).isDefined());
        assertFalse(Features.FILLS.of(BranchBadges.KEY, OFF).isDefined());
    }

    @Test
    void gitHubGroupsTheBranchesInStacksWhileItIsOn() {
        assertTrue(Features.FILLS.of(BranchGroups.KEY, ON).isDefined());
        assertFalse(Features.FILLS.of(BranchGroups.KEY, OFF).isDefined());
    }
}
