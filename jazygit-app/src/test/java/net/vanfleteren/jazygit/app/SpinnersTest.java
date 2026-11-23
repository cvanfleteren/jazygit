package net.vanfleteren.jazygit.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import java.time.Instant;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.features.branches.branch.push.PushCmd;
import net.vanfleteren.jazygit.features.branches.panes.branches.BranchesPanel;
import net.vanfleteren.jazygit.kernel.state.Jobs;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.load.LoadStatus;
import net.vanfleteren.jazygit.kernel.state.view.BranchesTab;
import net.vanfleteren.jazygit.kernel.state.view.ViewMsg;
import net.vanfleteren.jazygit.kernel.ui.text.TimeText;
import org.junit.jupiter.api.Test;

/**
 * Frames are drawn for the spinners exactly while one shows.
 */
class SpinnersTest {

    private static final Model LOADED = TestModels.loaded(SampleData.git());

    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");

    private static final BranchRef.Local FEATURE = new BranchRef.Local(new BranchName("feature"));

    @Test
    void noneShowsWhenNothingRuns() {
        assertFalse(Spinners.shown(LOADED, NOW));
    }

    @Test
    void aBusyBranchShowsOneInTheListThatHasIt() {
        Model busy = TestModels.busy(LOADED, FEATURE, PushCmd.PUSH);

        assertTrue(Spinners.shown(busy, NOW));
        assertFalse(BranchesPanel.showsSpinner(
                Update.update(busy, new ViewMsg.TabChosen(PaneId.BRANCHES, BranchesTab.REMOTE.id()))
                        .model()));
        // The line at the bottom still says that the push runs.
        assertTrue(Spinners.shown(
                Update.update(busy, new ViewMsg.TabChosen(PaneId.BRANCHES, BranchesTab.REMOTE.id()))
                        .model(),
                NOW));
    }

    @Test
    void theFetchLineShowsOneWhileACommandRunsThatShowsAndWhileWhatShowedLingers() {
        Model running = LOADED.mapJobs(jobs -> jobs.withStarted(List.of(new LoadStatus()), NOW));
        Model lingering = running.mapJobs(jobs -> jobs.withDone(LOADED.jobs().nextId(), NOW.plusSeconds(1)));

        // Not before it ran long enough to show (ADR 124).
        assertFalse(Spinners.shown(running, NOW));
        assertTrue(Spinners.shown(running, NOW.plus(Jobs.SHOWN_AFTER)));
        assertTrue(Spinners.shown(lingering, NOW.plusSeconds(1).plus(Jobs.LINGER)));
        // One frame longer, so that it is drawn gone.
        assertTrue(
                Spinners.shown(lingering, NOW.plusSeconds(1).plus(Jobs.LINGER).plus(TimeText.SPINNER_FRAME)));
        assertFalse(Spinners.shown(
                lingering,
                NOW.plusSeconds(1)
                        .plus(Jobs.LINGER)
                        .plus(TimeText.SPINNER_FRAME)
                        .plusMillis(1)));
    }
}
