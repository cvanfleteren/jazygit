package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.layout.Size;
import dev.tamboui.toolkit.Toolkit;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
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
                        Option.of(new PullRequest(42, new GhBranch("b"), false, Checks.PASSING, Review.APPROVED)),
                        Option.none()),
                Option.of("Pushing |"));

        assertEquals("feature  Pushing |", row.text());
        assertEquals(Option.of("  3d  ↑2 ↓5  ⇡1 ⇣58  ✓ #42  +  wt"), row.detailText());
        assertEquals(2, row.height());
    }

    @Test
    void aBranchShowsItsParentAfterHowFarItIsBehindIt() {
        LoadableList.Row row = LocalBranchesList.row(
                Fixtures.branch(new BranchName("feat/b"), false, new Sha("1")),
                Duration.ofDays(2),
                Option.of(new BaseDistance(new BranchRef.Local(new BranchName("feat/a")), new Sha("1"), 0, 1)),
                Option.of(new BranchName("feat/a")),
                BranchSegs.gitHub(
                        Option.of(new PullRequest(97, new GhBranch("feat/b"), false, Checks.PASSING, Review.NONE)),
                        Option.none()),
                Option.none());

        assertEquals(Option.of("  2d  ⇣1 on feat/a  ✓ #97"), row.detailText());
    }

    @Test
    void aBranchNotBehindItsParentShowsTheParentAlone() {
        LoadableList.Row row = LocalBranchesList.row(
                Fixtures.branch(new BranchName("feat/b"), false, new Sha("1")),
                Duration.ofDays(2),
                Option.none(),
                Option.of(new BranchName("feat/a")),
                List.empty(),
                Option.none());

        assertEquals(Option.of("  2d  on feat/a"), row.detailText());
        assertEquals(
                Option.of(Theme.Classes.DIM),
                row.detail().map(segs -> segs.last()).flatMap(LoadableList.Seg::cssClass));
    }

    @Test
    void aLongParentShowsWholeWhereItFits() throws Exception {
        assertEquals(
                "  2d  on feature/a-very-long-name  ✓ #42  +", onLongParent(50).stripTrailing());
    }

    @Test
    void aLongParentIsCutWhereItDoesNotFitSoWhatComesAfterItStaysInView() throws Exception {
        assertEquals("  2d  on fe...  ✓ #42  +", onLongParent(24));
    }

    @Test
    void aLongParentGoesBeforeThePullRequestIsCut() throws Exception {
        assertEquals("  2d    ✓ #42  +", onLongParent(16));
    }

    /**
     * Line 2 of a branch on a long parent with a pull request after it, drawn {@code width} columns wide. The branch
     * is level with its base, so the parent comes first in its group, which puts a space before it: what a row is
     * most often, and where the parent has to keep being the first to be cut.
     */
    private static String onLongParent(int width) throws Exception {
        LoadableList.Row row = LocalBranchesList.row(
                Fixtures.branch(new BranchName("feat/b"), false, new Sha("1")),
                Duration.ofDays(2),
                Option.none(),
                Option.of(new BranchName("feature/a-very-long-name")),
                BranchSegs.gitHub(
                        Option.of(new PullRequest(42, new GhBranch("b"), false, Checks.PASSING, Review.APPROVED)),
                        Option.none()),
                Option.none());
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(row::element)) {
            return RenderedText.ofSize(runner, row::element, width, 2)
                    .lines()
                    .toList()
                    .get(1);
        }
    }

    private static final BranchName FEATURE = new BranchName("feature");
    private static final BranchName FEAT_A = new BranchName("feat/a");

    private static Model withParent(BranchName parent) {
        return TestModels.loaded().mapRepo(repo -> repo.withParents(HashMap.of(FEATURE, parent)));
    }

    private static Option<BranchName> shownParent(Model model) {
        return LocalBranchesList.shownParent(
                model,
                model.repo()
                        .branches()
                        .toOption()
                        .get()
                        .find(b -> b.name().equals(FEATURE))
                        .get());
    }

    @Test
    void aBranchShowsAParentThatIsNoRoot() {
        assertEquals(Option.of(FEAT_A), shownParent(withParent(FEAT_A)));
    }

    @Test
    void aBranchBuiltOnARootShowsNoParent() {
        BranchName main = new BranchName("main");
        Model onMain = withParent(main)
                .mapRepo(repo -> repo.withBranches(repo.branches()
                        .map(branches -> branches.map(b -> b.name().equals(main)
                                ? Fixtures.branch(main, true, b.tip(), Instant.EPOCH, false, true)
                                : b))));

        assertEquals(Option.none(), shownParent(onMain));
        assertEquals(
                Option.none(),
                shownParent(withParent(FEAT_A)
                        .mapGitHub(gitHub -> gitHub.withProtectedBranches(Option.of(HashSet.of(FEAT_A))))));
    }

    @Test
    void aBranchInAStackOnGitHubShowsNoParent() {
        PullRequestStack stack = new PullRequestStack(
                12,
                2,
                2,
                List.of(
                        new PullRequestStack.Entry(1, 96, State.OPEN, new GhBranch("feat/a"), new GhBranch("main")),
                        new PullRequestStack.Entry(
                                2, 97, State.OPEN, new GhBranch("feature"), new GhBranch("feat/a"))));
        PullRequest stacked = new PullRequest(
                97,
                new GhBranch("feature"),
                Option.of(new GhBranch("feat/a")),
                false,
                Checks.NONE,
                Review.NONE,
                Option.of(stack),
                Option.none());

        assertEquals(
                Option.none(),
                shownParent(withParent(FEAT_A)
                        .mapGitHub(gitHub -> gitHub.withPullRequests(Option.of(HashMap.of(FEATURE, stacked))))));
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
