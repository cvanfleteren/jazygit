package net.vanfleteren.jazygit.features.github.badges;

import static net.vanfleteren.jazygit.features.github.PullRequestFixtures.FEATURE;
import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.function.Function;
import net.vanfleteren.jazygit.core.gh.model.MergeStatus;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.core.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.core.gh.model.StackId;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.ForgeBranch;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.features.github.PullRequestFixtures;
import net.vanfleteren.jazygit.features.github.state.GitHubChecks;
import net.vanfleteren.jazygit.features.github.state.GitHubState;
import net.vanfleteren.jazygit.features.github.state.Waiting;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.ui.BranchBadges;
import net.vanfleteren.jazygit.kernel.ui.text.ShownTime;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import org.junit.jupiter.api.Test;

/**
 * How a branch is doing on GitHub, as the branches pane shows it among its badges (ADR 167).
 */
class GitHubBadgeTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    @Test
    void aPullRequestShowsItsNumberItsChecksAndItsReview() {
        assertEquals(List.of(), GitHubBadge.pullRequest(Option.none()));
        assertEquals(
                List.of(
                        LoadableList.Seg.styled(" ✓", Theme.Classes.CHECKS_PASSED),
                        LoadableList.Seg.styled(" #42", Theme.Classes.PULL_REQUEST),
                        LoadableList.Seg.styled("  +", Theme.Classes.REVIEW_APPROVED)),
                GitHubBadge.pullRequest(Option.of(new PullRequest(
                        new PrNumber(42), new ForgeBranch("b"), false, Checks.PASSING, Review.APPROVED))));
        assertEquals(
                List.of(
                        LoadableList.Seg.styled(" ✗", Theme.Classes.CHECKS_FAILED),
                        LoadableList.Seg.styled(" #40", Theme.Classes.PULL_REQUEST),
                        LoadableList.Seg.styled("  ?", Theme.Classes.DIM)),
                GitHubBadge.pullRequest(Option.of(new PullRequest(
                        new PrNumber(40), new ForgeBranch("b"), false, Checks.FAILING, Review.REQUIRED))));
        assertEquals(
                List.of(
                        LoadableList.Seg.styled(" ●", Theme.Classes.CHECKS_RUNNING),
                        LoadableList.Seg.styled(" #38", Theme.Classes.PULL_REQUEST),
                        LoadableList.Seg.styled("  −", Theme.Classes.REVIEW_CHANGES)),
                GitHubBadge.pullRequest(Option.of(new PullRequest(
                        new PrNumber(38), new ForgeBranch("b"), false, Checks.PENDING, Review.CHANGES_REQUESTED))));
        // A draft without checks or reviews shows only its number, dimmed.
        assertEquals(
                List.of(LoadableList.Seg.styled(" #37", Theme.Classes.DIM)),
                GitHubBadge.pullRequest(Option.of(
                        new PullRequest(new PrNumber(37), new ForgeBranch("b"), true, Checks.NONE, Review.NONE))));
    }

    @Test
    void aStackedPullRequestShowsWhereItSitsInItsStackLast() {
        assertEquals(
                List.of(
                        LoadableList.Seg.styled(" ✓", Theme.Classes.CHECKS_PASSED),
                        LoadableList.Seg.styled(" #95", Theme.Classes.PULL_REQUEST),
                        LoadableList.Seg.styled("  2/3", Theme.Classes.DIM)),
                GitHubBadge.pullRequest(Option.of(new PullRequest(
                        new PrNumber(95),
                        new ForgeBranch("b"),
                        false,
                        Checks.PASSING,
                        Review.NONE,
                        Option.of(new PullRequestStack(
                                new StackId(96),
                                2,
                                3,
                                List.of(
                                        new PullRequestStack.Entry(1, new PrNumber(94), State.OPEN),
                                        new PullRequestStack.Entry(2, new PrNumber(95), State.OPEN),
                                        new PullRequestStack.Entry(3, new PrNumber(97), State.OPEN))))))));
    }

    @Test
    void aMergeShowsHowItGoesInsteadOfTheChecksAndTheReview() {
        Option<PullRequest> pr = Option.of(
                new PullRequest(new PrNumber(42), new ForgeBranch("b"), false, Checks.FAILING, Review.APPROVED));

        assertEquals(
                List.of(
                        LoadableList.Seg.styled(" »", Theme.Classes.DIM),
                        LoadableList.Seg.styled(" #42", Theme.Classes.PULL_REQUEST)),
                GitHubBadge.pullRequest(pr, Option.of(new MergeStatus.Pending("u-1"))));
        assertEquals(
                " ≡",
                GitHubBadge.pullRequest(pr, Option.of(new MergeStatus.Enqueued()))
                        .head()
                        .text());
        assertEquals(
                LoadableList.Seg.styled(" !", Theme.Classes.CHECKS_FAILED),
                GitHubBadge.pullRequest(pr, Option.of(new MergeStatus.Failed("no")))
                        .head());
        assertEquals(
                " ◆",
                GitHubBadge.pullRequest(pr, Option.of(new MergeStatus.Merged()))
                        .head()
                        .text());
    }

    @Test
    void aBranchShowsItsPullRequestElseTheChecksOnItsTip() {
        PullRequest pr = new PullRequest(new PrNumber(42), new ForgeBranch("b"), false, Checks.NONE, Review.NONE);

        assertEquals(
                List.of(LoadableList.Seg.styled(" #42", Theme.Classes.PULL_REQUEST)),
                GitHubBadge.gitHub(Option.of(pr), Option.of(Checks.FAILING)));
        assertEquals(
                List.of(LoadableList.Seg.styled(" ✗", Theme.Classes.CHECKS_FAILED)),
                GitHubBadge.gitHub(Option.none(), Option.of(Checks.FAILING)));
        assertEquals(List.of(), GitHubBadge.gitHub(Option.none(), Option.of(Checks.NONE)));
        assertEquals(List.of(), GitHubBadge.gitHub(Option.none(), Option.none()));
    }

    @Test
    void runningChecksSayWhenTheyAreExpectedToBeDone() {
        PullRequest running =
                new PullRequest(new PrNumber(38), new ForgeBranch("b"), false, Checks.PENDING, Review.APPROVED);

        assertEquals(
                List.of(
                        LoadableList.Seg.styled(" ●", Theme.Classes.CHECKS_RUNNING),
                        LoadableList.Seg.styled(" ~4m", Theme.Classes.DIM),
                        LoadableList.Seg.styled(" #38", Theme.Classes.PULL_REQUEST),
                        LoadableList.Seg.styled("  +", Theme.Classes.REVIEW_APPROVED)),
                GitHubBadge.gitHub(Option.of(running), Option.none(), Option.none(), Option.of("~4m")));
        assertEquals(
                List.of(
                        LoadableList.Seg.styled(" ●", Theme.Classes.CHECKS_RUNNING),
                        LoadableList.Seg.styled(" late", Theme.Classes.DIM)),
                GitHubBadge.gitHub(Option.none(), Option.none(), Option.of(Checks.PENDING), Option.of("late")));
        // Only while they run, and not while the pull request is merged.
        assertEquals(
                List.of(LoadableList.Seg.styled(" ✓", Theme.Classes.CHECKS_PASSED)),
                GitHubBadge.gitHub(Option.none(), Option.none(), Option.of(Checks.PASSING), Option.of("~4m")));
        assertEquals(
                List.of(
                        LoadableList.Seg.styled(" »", Theme.Classes.DIM),
                        LoadableList.Seg.styled(" #38", Theme.Classes.PULL_REQUEST)),
                GitHubBadge.gitHub(
                        Option.of(running),
                        Option.of(new MergeStatus.Pending("u-1")),
                        Option.none(),
                        Option.of("~4m")));
    }

    @Test
    void checksThatAllWaitToStartSayHowLongTheyHave() {
        PullRequest running =
                new PullRequest(new PrNumber(38), new ForgeBranch("b"), false, Checks.PENDING, Review.APPROVED);
        Option<Waiting> waiting = Option.of(new Waiting(Option.of(NOW.minusSeconds(200))));

        assertEquals(
                List.of(
                        LoadableList.Seg.styled(" ◌", Theme.Classes.DIM),
                        LoadableList.Seg.styled(" 3m", Theme.Classes.DIM)),
                GitHubBadge.gitHub(
                        Option.none(), Option.none(), Option.of(Checks.PENDING), waiting, Option.of("~4m"), NOW));
        assertEquals(
                List.of(
                        LoadableList.Seg.styled(" ◌", Theme.Classes.DIM),
                        LoadableList.Seg.styled(" 3m", Theme.Classes.DIM),
                        LoadableList.Seg.styled(" #38", Theme.Classes.PULL_REQUEST),
                        LoadableList.Seg.styled("  +", Theme.Classes.REVIEW_APPROVED)),
                GitHubBadge.gitHub(Option.of(running), Option.none(), Option.none(), waiting, Option.of("~4m"), NOW));
        // Failed checks fail, also while another waits.
        assertEquals(
                List.of(LoadableList.Seg.styled(" ✗", Theme.Classes.CHECKS_FAILED)),
                GitHubBadge.gitHub(
                        Option.none(), Option.none(), Option.of(Checks.FAILING), waiting, Option.of("~4m"), NOW));
    }

    @Test
    void howLongChecksHaveWaitedChangesTheMomentTheTimeOfTheirBranchSays() {
        Option<Waiting> waiting = Option.of(new Waiting(Option.of(NOW.minusSeconds(200))));
        ShownTime shown = ShownTime.reading(GitHubBadge.timed(waiting, Option.none(), false));
        Function<Instant, List<LoadableList.Seg>> drawn = at ->
                GitHubBadge.gitHub(Option.none(), Option.none(), Option.of(Checks.PENDING), waiting, Option.none(), at);
        Instant next = ShownTime.next(List.of(shown), NOW).get();

        assertEquals(NOW.plusSeconds(40), next);
        assertEquals(drawn.apply(NOW), drawn.apply(next.minusMillis(1)));
        assertNotEquals(drawn.apply(NOW), drawn.apply(next));
    }

    @Test
    void checksThatWaitToStartForAReRunSayNotHowLongNorWhenTheyAreDone() {
        assertEquals(
                List.of(LoadableList.Seg.styled(" ◌", Theme.Classes.DIM)),
                GitHubBadge.gitHub(
                        Option.none(),
                        Option.none(),
                        Option.of(Checks.PENDING),
                        Option.of(new Waiting(Option.none())),
                        Option.of("~4m"),
                        NOW));
    }

    @Test
    void aLocalBranchWithAPullRequestShowsIt() {
        BranchBadges.Shown shown = GitHubBadge.of(PullRequestFixtures.withPr(), new BranchRef.Local(FEATURE));

        assertEquals(
                List.of(
                        LoadableList.Seg.styled(" ✓", Theme.Classes.CHECKS_PASSED),
                        LoadableList.Seg.styled(" #42", Theme.Classes.PULL_REQUEST),
                        LoadableList.Seg.styled("  +", Theme.Classes.REVIEW_APPROVED)),
                shown.segs());
        // Its checks are done, so nothing of it changes with the time.
        assertEquals("", shown.timed().apply(NOW));
    }

    @Test
    void theBranchOnTheRemoteOfAPullRequestShowsItToo() {
        // A remote is on GitHub once the checks of the branches said so.
        Model model = PullRequestFixtures.withPr()
                .mapPart(
                        GitHubState.PART,
                        state -> state.withBranchChecks(
                                Option.of(new GitHubChecks(HashSet.of(new RemoteName("origin")), HashMap.empty()))));

        BranchBadges.Shown shown = GitHubBadge.of(model, new BranchRef.Remote(new RemoteBranch("origin", FEATURE)));

        assertTrue(
                shown.segs().contains(LoadableList.Seg.styled(" #42", Theme.Classes.PULL_REQUEST)), shown.toString());
    }

    @Test
    void aLocalBranchThatIsNotReadShowsNothing() {
        assertEquals(
                BranchBadges.Shown.NONE,
                GitHubBadge.of(PullRequestFixtures.withPr(), new BranchRef.Local(new BranchName("unknown"))));
    }

    @Test
    void checksThatDoNotAllWaitSayWhenTheyAreExpectedToBeDone() {
        assertEquals(
                List.of(
                        LoadableList.Seg.styled(" ●", Theme.Classes.CHECKS_RUNNING),
                        LoadableList.Seg.styled(" ~4m", Theme.Classes.DIM)),
                GitHubBadge.gitHub(
                        Option.none(), Option.none(), Option.of(Checks.PENDING), Option.none(), Option.of("~4m"), NOW));
    }
}
