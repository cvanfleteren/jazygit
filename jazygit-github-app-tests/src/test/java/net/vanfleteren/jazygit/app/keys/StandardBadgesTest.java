package net.vanfleteren.jazygit.app.keys;

import static org.junit.jupiter.api.Assertions.*;

import net.vanfleteren.jazygit.features.github.GitHubFeature;
import net.vanfleteren.jazygit.kernel.ui.Feature;
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
        assertEquals(
                Features.ALL.flatMap(Feature::badges), BranchBadges.standard().badges());
        assertEquals(
                GitHubFeature.FEATURE.badges().size(),
                Features.ALL
                        .find(feature -> feature.applying().isDefined())
                        .get()
                        .badges()
                        .size());
        assertTrue(GitHubFeature.FEATURE.badges().nonEmpty());
    }
}
