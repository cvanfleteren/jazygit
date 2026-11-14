package net.vanfleteren.jazygit;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.state.Freshness;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TerminalMsg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.view.BranchesTab;
import org.junit.jupiter.api.Test;

class ShownClocksTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

    /**
     * The repository, fetched {@code seconds} before NOW: how long ago changes sooner than the ages of its branches,
     * which are days.
     */
    private static Model fetched(int seconds) {
        return TestModels.loaded()
                .withNow(NOW)
                .mapJobs(jobs -> jobs.withLastFetch(Freshness.NEVER.succeeded("origin", NOW.minusSeconds(seconds))));
    }

    @Test
    void theAppIsWokenWhenTheFirstTimeShownChanges() {
        assertEquals(Option.of(NOW.plusSeconds(2)), ShownClocks.next(fetched(28), NOW));
    }

    @Test
    void withNoTimeShownTheAppIsNotWoken() {
        // The worktrees show no age, and nothing was fetched yet.
        Model worktrees =
                TestModels.loaded().withNow(NOW).mapRepoView(view -> view.withBranchesTab(BranchesTab.WORKTREES));

        assertEquals(Option.none(), ShownClocks.next(worktrees, NOW));
    }

    @Test
    void whileAwayATimeShownMovesOnNoSoonerThanEveryFiveSeconds() {
        Model away = Update.update(fetched(28), new TerminalMsg.Focused(false)).model();
        Model later = Update.update(fetched(13), new TerminalMsg.Focused(false)).model();

        assertEquals(Option.of(NOW.plus(Model.AWAY_CLOCK)), ShownClocks.next(away, NOW));
        assertEquals(Option.of(NOW.plusSeconds(7)), ShownClocks.next(later, NOW));
    }
}
