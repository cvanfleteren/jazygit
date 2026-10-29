package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.layout.Size;
import dev.tamboui.toolkit.Toolkit;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.git.model.*;
import org.junit.jupiter.api.Test;

class LocalBranchesListTest {

    @Test
    void theCurrentBranchStandsOutByTheStyleOfItsNameAlone() {
        LoadableList.Row current = LocalBranchesList.row(
                Fixtures.branch(new BranchName("main"), true, new Sha("1")),
                Duration.ofMinutes(5),
                List.empty(),
                Option.none());
        LoadableList.Row other = LocalBranchesList.row(
                Fixtures.branch(new BranchName("feature"), false, new Sha("2")),
                Duration.ofMinutes(5),
                List.empty(),
                Option.none());

        assertEquals("main", current.text());
        assertEquals(List.of(Option.of(Theme.Classes.CURRENT)), current.segs().map(LoadableList.Seg::cssClass));
        assertEquals("feature", other.text());
        assertEquals(List.of(Option.none()), other.segs().map(LoadableList.Seg::cssClass));
    }

    @Test
    void aBranchShowsItsNameAndSpinnerAboveItsAgeSyncPullRequestAndWorktree() {
        Branch branch = new Branch(
                new BranchName("feature"),
                false,
                new Sha("1"),
                Instant.EPOCH,
                Option.none(),
                true,
                false,
                new Tracking.Tracks(new Upstream(
                        new BranchRef.Remote(new RemoteBranch("origin", new BranchName("feature"))), 2, 5)),
                Option.of(Path.of("/repos/linked")));
        LoadableList.Row row = LocalBranchesList.row(
                branch,
                Duration.ofDays(3),
                BranchSegs.gitHub(
                        Option.of(new PullRequest(42, "b", false, Checks.PASSING, Review.APPROVED)), Option.none()),
                Option.of("Pushing |"));

        assertEquals("feature  Pushing |", row.text());
        assertEquals(Option.of("  3d  ↑2 ↓5  #42 ✓  +  wt"), row.detailText());
        assertEquals(2, row.height());
    }

    @Test
    void aBranchWithNothingToSayStillTakesTwoLines() {
        LoadableList.Row row = LocalBranchesList.row(
                Fixtures.branch(new BranchName("main"), false, new Sha("1")),
                Duration.ofDays(7),
                List.empty(),
                Option.none());

        assertEquals(Option.of("  7d"), row.detailText());
        assertEquals(2, row.height());
    }

    @Test
    void aLongNameIsCutOffAndTheSpinnerStaysInView() throws Exception {
        LoadableList.Row row = LocalBranchesList.row(
                Fixtures.branch(new BranchName("feat/payment-retry-backoff"), false, new Sha("1")),
                Duration.ofDays(3),
                BranchSegs.gitHub(Option.none(), Option.of(Checks.FAILING)),
                Option.of("Pushing |"));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> Toolkit.text(""), new Size(40, 8))) {
            assertEquals(
                    "feat/payme...  Pushing |\n  3d  ✗                 \n",
                    RenderedText.ofSize(testRunner, row::element, 24, 2));
        }
    }
}
