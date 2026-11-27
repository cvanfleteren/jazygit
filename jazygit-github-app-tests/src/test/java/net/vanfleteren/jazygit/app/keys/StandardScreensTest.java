package net.vanfleteren.jazygit.app.keys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.features.github.builds.screen.BuildsScreen;
import net.vanfleteren.jazygit.features.github.prs.PrOverview;
import net.vanfleteren.jazygit.kernel.state.preferences.Screen;
import net.vanfleteren.jazygit.kernel.ui.screen.ScreenSpec;
import net.vanfleteren.jazygit.kernel.ui.screen.Screens;
import org.junit.jupiter.api.Test;

/**
 * The screens the left of the window shows, as the app gives them (ADR 168).
 */
class StandardScreensTest {

    @Test
    void theViewsFindTheScreensOfTheAppAsAService() {
        assertSame(new StandardScreens().screens(), Screens.standard());
    }

    @Test
    void theRepositoryComesFirstThenThePullRequestsAndTheBuildsGitHubBrings() {
        assertEquals(
                List.of(Screen.REPOSITORY, PrOverview.SCREEN, BuildsScreen.SCREEN),
                Screens.standard().all().map(ScreenSpec::screen));
    }

    @Test
    void theLayoutOfThePullRequestsIsKeptWhereItWas() {
        // config.toml is read as before: nothing needs migrating.
        assertEquals("ui.pull-requests-width", PrOverview.SCREEN.width().path());
        assertEquals("ui.pull-requests-heights", PrOverview.SCREEN.heights().path());
        assertEquals(
                PrOverview.SCREEN.panes().size() - 1,
                PrOverview.SCREEN.heights().initial().size());
    }
}
