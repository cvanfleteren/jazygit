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

    private static final BranchRef ORIGIN_MAIN =
            new BranchRef.Remote(new RemoteBranch("origin", new BranchName("main")));

    @Test
    void theCurrentBranchStandsOutByTheStyleOfItsNameAlone() {
        LoadableList.Row current = LocalBranchesList.row(
                Fixtures.branch(new BranchName("main"), true, new Sha("1")),
                Duration.ofMinutes(5),
                Option.none(),
                List.empty(),
                Option.none());
        LoadableList.Row other = LocalBranchesList.row(
                Fixtures.branch(new BranchName("feature"), false, new Sha("2")),
                Duration.ofMinutes(5),
                Option.none(),
                List.empty(),
                Option.none());

        assertEquals("main", current.text());
        assertEquals(List.of(Option.of(Theme.Classes.CURRENT)), current.segs().map(LoadableList.Seg::cssClass));
        assertEquals("feature", other.text());
        assertEquals(List.of(Option.none()), other.segs().map(LoadableList.Seg::cssClass));
    }

    @Test
    void aBranchShowsItsNameAndSpinnerAboveItsAgeSyncBaseDistancePullRequestAndWorktree() {
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
                Option.of(new BaseDistance(ORIGIN_MAIN, new Sha("1"), 1, 58)),
                BranchSegs.gitHub(
                        Option.of(new PullRequest(42, "b", false, Checks.PASSING, Review.APPROVED)), Option.none()),
                Option.of("Pushing |"));

        assertEquals("feature  Pushing |", row.text());
        assertEquals(Option.of("  3d  ↑2 ↓5  ⇡1 ⇣58  ✓ #42  +  wt"), row.detailText());
        assertEquals(2, row.height());
    }

    @Test
    void aBranchWithNothingToSayStillTakesTwoLines() {
        LoadableList.Row row = LocalBranchesList.row(
                Fixtures.branch(new BranchName("main"), false, new Sha("1")),
                Duration.ofDays(7),
                Option.none(),
                List.empty(),
                Option.none());

        assertEquals(Option.of("  7d"), row.detailText());
        assertEquals(2, row.height());
    }

    @Test
    void aBranchEvenWithItsBaseSaysNothingOfIt() {
        assertEquals(Option.of("  7d"), detail(Fixtures.branch(new BranchName("feature"), false, new Sha("1")), 0, 0));
    }

    @Test
    void aBranchOnlyAheadOfItsBaseOrOnlyBehindItShowsThatCountAlone() {
        Branch branch = Fixtures.branch(new BranchName("feature"), false, new Sha("1"));

        assertEquals(Option.of("  7d  ⇡3"), detail(branch, 3, 0));
        assertEquals(Option.of("  7d  ⇣4"), detail(branch, 0, 4));
    }

    @Test
    void aBranchWhoseBaseIsItsUpstreamShowsOnlyHowFarItIsFromItsUpstream() {
        Branch branch = new Branch(
                new BranchName("feature"),
                false,
                new Sha("1"),
                Instant.EPOCH,
                Option.none(),
                false,
                false,
                new Tracking.Tracks(new Upstream(ORIGIN_MAIN, 2, 5)),
                Option.none());

        assertEquals(Option.of("  7d  ↑2 ↓5"), detail(branch, 2, 5));
    }

    private static Option<String> detail(Branch branch, int ahead, int behind) {
        return LocalBranchesList.row(
                        branch,
                        Duration.ofDays(7),
                        Option.of(new BaseDistance(ORIGIN_MAIN, new Sha("1"), ahead, behind)),
                        List.empty(),
                        Option.none())
                .detailText();
    }

    @Test
    void aLongNameIsCutOffAndTheSpinnerStaysInView() throws Exception {
        LoadableList.Row row = LocalBranchesList.row(
                Fixtures.branch(new BranchName("feat/payment-retry-backoff"), false, new Sha("1")),
                Duration.ofDays(3),
                Option.none(),
                BranchSegs.gitHub(Option.none(), Option.of(Checks.FAILING)),
                Option.of("Pushing |"));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> Toolkit.text(""), new Size(40, 8))) {
            assertEquals(
                    "feat/payme...  Pushing |\n  3d  ✗                 \n",
                    RenderedText.ofSize(testRunner, row::element, 24, 2));
        }
    }
}
