package net.vanfleteren.jazygit.app;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Freshness;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.TerminalMsg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.view.BranchesTab;
import net.vanfleteren.jazygit.kernel.state.view.CommitFilesView;
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
    void theAgeOfABranchWakesTheAppAsTheBranchesPaneFillsItsClock() {
        // The branches pane brings its times through the slot (ADR 211); nothing was fetched.
        Model loaded = TestModels.loaded();
        Instant active = loaded.repo().branches().toOption().get().head().lastActivity();
        Instant now = active.plusSeconds(3 * 60 + 20);

        Option<Instant> next = ShownClocks.next(loaded.withNow(now), now);

        assertTrue(next.exists(at -> at.isAfter(now) && !at.isAfter(now.plusSeconds(40))), next.toString());
    }

    @Test
    void theFilesKeptWhileTheNextAreReadWakeTheAppAsTheFilesPaneFillsItsClock() {
        // The files pane brings its times through the slot (ADR 211); the worktrees show no age.
        CommitFilesView first = CommitFilesView.of(new Changes.OfCommit(new Sha("aaaa")), PaneId.COMMITS, PaneId.FILES)
                .withFollowed(true)
                .withFiles(new Loadable.Loaded<>(List.empty()));
        CommitFilesView next = CommitFilesView.of(new Changes.OfCommit(new Sha("bbbb")), PaneId.COMMITS, PaneId.FILES)
                .withFollowed(true)
                .after(Option.of(first), NOW);
        Model listing = TestModels.loaded()
                .withNow(NOW)
                .mapRepoView(view -> view.withBranchesTab(BranchesTab.WORKTREES)
                        .mapFiles(files -> files.withCommitFiles(Option.of(next))));

        assertEquals(Option.of(NOW.plus(CommitFilesView.KEPT_WHILE_READ)), ShownClocks.next(listing, NOW));
    }

    @Test
    void whileAwayATimeShownMovesOnNoSoonerThanEveryFiveSeconds() {
        Model away = Update.update(fetched(28), new TerminalMsg.Focused(false)).model();
        Model later = Update.update(fetched(13), new TerminalMsg.Focused(false)).model();

        assertEquals(Option.of(NOW.plus(Model.AWAY_CLOCK)), ShownClocks.next(away, NOW));
        assertEquals(Option.of(NOW.plusSeconds(7)), ShownClocks.next(later, NOW));
    }
}
