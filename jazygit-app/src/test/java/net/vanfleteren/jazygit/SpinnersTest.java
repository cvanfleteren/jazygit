package net.vanfleteren.jazygit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Either;
import java.time.Instant;
import net.vanfleteren.jazygit.feature.branch.push.PushCmd;
import net.vanfleteren.jazygit.feature.branch.tabs.BranchesTabMsg;
import net.vanfleteren.jazygit.feature.github.checks.ChecksPopup;
import net.vanfleteren.jazygit.feature.github.comments.CommentsPopup;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.BranchesTab;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Jobs;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Reading;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.TimeText;
import org.junit.jupiter.api.Test;

/**
 * Frames are drawn for the spinners exactly while one shows.
 */
class SpinnersTest {

    private static final Model LOADED = TestModels.loaded(new SampleData());
    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");
    private static final BranchRef.Local FEATURE = new BranchRef.Local(new BranchName("feature"));
    private static final PullRequestPopup PULL_REQUEST = PullRequestPopup.loading(
            new BranchName("feature"),
            new PullRequest(42, "feature", true, Checks.PASSING, Review.APPROVED),
            Instant.EPOCH,
            0);

    @Test
    void noneShowsWhenNothingRuns() {
        assertFalse(Spinners.shown(LOADED, NOW));
    }

    @Test
    void aBusyBranchShowsOneInTheListThatHasIt() {
        Model busy = TestModels.busy(LOADED, FEATURE, PushCmd.PUSH);

        assertTrue(Spinners.shown(busy, NOW));
        assertFalse(BranchesPanel.showsSpinner(Update.update(busy, new BranchesTabMsg.Chosen(BranchesTab.REMOTE))
                .model()));
        // The line at the bottom still says that the push runs.
        assertTrue(Spinners.shown(
                Update.update(busy, new BranchesTabMsg.Chosen(BranchesTab.REMOTE))
                        .model(),
                NOW));
    }

    @Test
    void thePullRequestDialogShowsOneWhileItsDetailsAreRead() {
        assertTrue(Spinners.shown(LOADED.openPopup(PULL_REQUEST), NOW));
        assertFalse(Spinners.shown(
                LOADED.openPopup(PULL_REQUEST.with(Either.left(TestModels.refused("offline")), 0)), NOW));
    }

    @Test
    void theCommentsDialogShowsOneWhileTheCommentsAreRead() {
        CommentsPopup loading = CommentsPopup.loading(PULL_REQUEST);

        assertTrue(Spinners.shown(LOADED.openPopup(loading), NOW));
        assertFalse(Spinners.shown(LOADED.openPopup(loading.read(Either.right(List.empty()))), NOW));
    }

    @Test
    void theChecksDialogShowsOneWhileItsChecksAreFirstReadAndReadAgainAsAsked() {
        ChecksPopup loading =
                ChecksPopup.loading(FEATURE, new RemoteBranch("origin", new BranchName("feature")), Instant.EPOCH);
        ChecksPopup read = loading.with(Either.left(TestModels.refused("offline")));

        assertTrue(Spinners.shown(LOADED.openPopup(loading), NOW));
        assertFalse(Spinners.shown(LOADED.openPopup(read), NOW));
        assertFalse(Spinners.shown(LOADED.openPopup(read.readingAgain(Reading.BY_ITSELF, NOW)), NOW));
        assertTrue(Spinners.shown(LOADED.openPopup(read.readingAgain(Reading.ASKED, NOW)), NOW));
    }

    @Test
    void theFetchLineShowsOneWhileACommandRunsAndWhileWhatShowedLingers() {
        Model running = LOADED.mapJobs(jobs -> jobs.withStarted(List.of(new Cmd.LoadStatus()), NOW));
        Model lingering = running.mapJobs(jobs -> jobs.withDone(LOADED.jobs().nextId(), NOW.plusSeconds(1)));

        assertTrue(Spinners.shown(running, NOW));
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
