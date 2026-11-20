package net.vanfleteren.jazygit.app.keys;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.vanfleteren.jazygit.features.github.GitHubFeature;
import net.vanfleteren.jazygit.kernel.ui.branches.BranchBadges;
import org.junit.jupiter.api.Test;

/**
 * The badges the branches pane shows, as the app gives them (ADR 167).
 */
class StandardBadgesTest {

    @Test
    void thePaneFindsTheBadgesOfTheAppAsAService() {
        assertSame(new StandardBadges().badges(), BranchBadges.standard());
    }

    @Test
    void theyHaveGitHubsBadge() {
        assertTrue(BranchBadges.standard().badges().containsAll(GitHubFeature.FEATURE.badges()));
        assertTrue(GitHubFeature.FEATURE.badges().nonEmpty());
    }
}
