package net.vanfleteren.jazygit.feature.github.show;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static net.vanfleteren.jazygit.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.feature.github.comment.CommentMsg;
import net.vanfleteren.jazygit.feature.github.comments.CommentsMsg;
import net.vanfleteren.jazygit.gh.model.CheckRun;
import net.vanfleteren.jazygit.gh.model.MergeMethod;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.*;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.gh.timing.CheckHistory;
import net.vanfleteren.jazygit.gh.timing.CheckSample;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.CheckTimes;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.ui.CheckRows;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.Theme;
import org.junit.jupiter.api.Test;

class PullRequestDialogTest {

    private static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");
    private static final PullRequest PR =
            new PullRequest(42, "support_rename", true, Checks.FAILING, Review.CHANGES_REQUESTED);
    private static final PullRequestDetails DETAILS = new PullRequestDetails(
            42,
            "Add rename support to the files panel",
            "cvf",
            State.OPEN,
            true,
            "support_rename",
            "main",
            Instant.parse("2026-10-10T08:00:00Z"),
            Instant.parse("2026-10-12T09:00:00Z"),
            120,
            34,
            7,
            3,
            Mergeable.MERGEABLE,
            Review.CHANGES_REQUESTED,
            5,
            new ReviewThreads(5, 5, 2, 12),
            List.of("enhancement", "ui"),
            List.of(
                    new Reviewer("alice", ReviewerState.APPROVED),
                    new Reviewer("bob", ReviewerState.CHANGES_REQUESTED),
                    new Reviewer("dave", ReviewerState.REQUESTED),
                    new Reviewer("carol", ReviewerState.COMMENTED)),
            List.of(
                    new Check("lint", Checks.PASSING),
                    new Check("integration", Checks.PENDING),
                    new Check("build", Checks.FAILING)));

    private static final PullRequestDetails READY = new PullRequestDetails(
            42,
            "Ready",
            "cvf",
            State.OPEN,
            false,
            "support_rename",
            "main",
            NOW,
            NOW,
            1,
            1,
            1,
            1,
            Mergeable.MERGEABLE,
            Review.APPROVED,
            0,
            ReviewThreads.NONE,
            List.empty(),
            List.empty(),
            List.empty());

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final PullRequestDialog dialog = new PullRequestDialog(dispatched::add, Clock.fixed(NOW, ZoneOffset.UTC));

    // DETAILS at commit head, into base, not known to be behind it.
    private static PullRequestDetails at(String head, String base) {
        return new PullRequestDetails(
                DETAILS.number(),
                DETAILS.title(),
                DETAILS.author(),
                DETAILS.state(),
                DETAILS.draft(),
                DETAILS.headBranch(),
                base,
                DETAILS.createdAt(),
                DETAILS.pushedAt(),
                DETAILS.additions(),
                DETAILS.deletions(),
                DETAILS.changedFiles(),
                DETAILS.commits(),
                DETAILS.mergeable(),
                DETAILS.review(),
                DETAILS.comments(),
                DETAILS.reviewThreads(),
                DETAILS.labels(),
                DETAILS.reviewers(),
                DETAILS.checks(),
                head,
                Option.none(),
                Option.none(),
                "");
    }

    /**
     * READY with {@code checks}, of repository {@code owner/repo}.
     */
    private static PullRequestDetails withChecks(List<Check> checks) {
        return new PullRequestDetails(
                READY.number(),
                READY.title(),
                READY.author(),
                READY.state(),
                READY.draft(),
                READY.headBranch(),
                READY.baseBranch(),
                READY.createdAt(),
                READY.pushedAt(),
                READY.additions(),
                READY.deletions(),
                READY.changedFiles(),
                READY.commits(),
                READY.mergeable(),
                READY.review(),
                READY.comments(),
                READY.reviewThreads(),
                READY.labels(),
                READY.reviewers(),
                checks,
                "abc123",
                Option.none(),
                Option.none(),
                "owner/repo");
    }

    /**
     * A check of workflow {@code Build} that is not done, of a run created {@code createdAgo} seconds before NOW: queued
     * without {@code startedAgo}, else running since then.
     */
    private static Check timed(String name, int createdAgo, Option<Integer> startedAgo) {
        return new Check(
                name,
                Checks.PENDING,
                Option.of(new CheckRun(
                        name.length(),
                        "Build",
                        "",
                        startedAgo.map(NOW::minusSeconds),
                        Option.none(),
                        1,
                        NOW.minusSeconds(createdAgo),
                        1,
                        "pull_request")));
    }

    /**
     * What was learned of {@code owner/repo}: package runs 60s, done 60s into its run; acceptance waits for it, runs
     * 190s and is done 254s in.
     */
    private static CheckTimes learned() {
        List<CheckSample> samples = List.range(0, 3)
                .flatMap(i -> List.of(
                        new CheckSample(
                                "Build/package",
                                i,
                                Duration.ofSeconds(60),
                                Duration.ofSeconds(60),
                                "pull_request",
                                NOW.minusSeconds(3600 + i)),
                        new CheckSample(
                                "Build/acceptance",
                                10 + i,
                                Duration.ofSeconds(190),
                                Duration.ofSeconds(254),
                                "pull_request",
                                NOW.minusSeconds(3600 + i))));
        return CheckTimes.of("owner/repo", CheckHistory.EMPTY.with(samples, NOW), Option.none());
    }

    private String timedScreen(List<Check> checks, CheckTimes times) throws Exception {
        return screen(
                PullRequestPopup.loading(new BranchName("support_rename"), PR).with(Either.right(withChecks(checks))),
                model -> model.mapGitHub(gitHub -> gitHub.withCheckTimes(Option.of(times))));
    }

    private static int hintLine(String screen) {
        return List.of(screen.split("\n")).indexWhere(line -> line.contains("(enter) select"));
    }

    @Test
    void closedModelShowsNoPopup() {
        assertTrue(dialog.render(TestModels.loaded()).isEmpty());
    }

    @Test
    void showsTheBranchWhileTheDetailsAreRead() throws Exception {
        String screen = screen(PullRequestPopup.loading(new BranchName("support_rename"), PR));

        assertTrue(screen.contains("Pull request #42"), screen);
        assertTrue(screen.contains("Branch  support_rename"), screen);
        assertTrue(
                screen.contains("Reading the pull request from GitHub… "
                        + net.vanfleteren.jazygit.ui.TimeText.spinnerFrame(NOW)),
                screen);
        assertTrue(screen.contains("(enter) select   (esc) cancel"), screen);
    }

    @Test
    void whileReadAgainTheDetailsStayWithASpinnerInTheTitle() throws Exception {
        String screen = screen(PullRequestPopup.loading(new BranchName("support_rename"), PR)
                .with(Either.right(DETAILS))
                .readingAgain());

        assertTrue(
                screen.contains("Pull request #42 " + net.vanfleteren.jazygit.ui.TimeText.spinnerFrame(NOW)), screen);
        assertTrue(screen.contains("Add rename support to the files panel"), screen);
        assertTrue(screen.contains("(R) Refresh"), screen);
    }

    @Test
    void detailsThatFailedToBeReadAgainAreMarkedStale() throws Exception {
        String screen = screen(PullRequestPopup.loading(new BranchName("support_rename"), PR)
                .with(Either.right(DETAILS))
                .readingAgain()
                .with(Either.left(refused("gh: offline"))));

        assertTrue(screen.contains("Pull request #42 · not up to date"), screen);
        assertTrue(screen.contains("Add rename support to the files panel"), screen);
    }

    @Test
    void refreshIsChosenWithCapitalR() throws Exception {
        Model model = TestModels.loaded()
                .openPopup(PullRequestPopup.loading(new BranchName("support_rename"), PR)
                        .with(Either.right(DETAILS)));
        try (ToolkitTestRunner runner = start(model)) {
            SettledPilot.of(runner).press('R');
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new PullRequestMsg.RefreshChosen()), dispatched);
    }

    @Test
    void aStackedPullRequestSaysWhatMergingItMergesToo() throws Exception {
        PullRequestDetails stacked = stacked(new PullRequestStack(
                96,
                3,
                3,
                List.of(
                        new PullRequestStack.Entry(1, 40, State.OPEN),
                        new PullRequestStack.Entry(2, 41, State.OPEN),
                        new PullRequestStack.Entry(3, 42, State.OPEN))));

        String screen = screen(
                PullRequestPopup.loading(new BranchName("support_rename"), PR).with(Either.right(stacked)));

        assertTrue(screen.contains("    Stack  #96 · 3 of 3 · merging also merges the 2 below"), screen);
        assertTrue(screen.contains("(m) Merge 3 PRs"), screen);
        assertTrue(screen.contains("(s) Squash and merge 3 PRs"), screen);
        assertTrue(screen.contains("(r) Rebase and merge 3 PRs"), screen);
    }

    @Test
    void theOnesBelowInAStackThatAreMergedAlreadyAreNotMergedAgain() throws Exception {
        PullRequestDetails stacked = stacked(new PullRequestStack(
                96,
                3,
                3,
                List.of(
                        new PullRequestStack.Entry(1, 40, State.MERGED),
                        new PullRequestStack.Entry(2, 41, State.OPEN),
                        new PullRequestStack.Entry(3, 42, State.OPEN))));

        String screen = screen(
                PullRequestPopup.loading(new BranchName("support_rename"), PR).with(Either.right(stacked)));

        assertTrue(screen.contains("    Stack  #96 · 3 of 3 · merging also merges the 1 below"), screen);
        assertTrue(screen.contains("(s) Squash and merge 2 PRs"), screen);
    }

    @Test
    void aStackWhoseOnesBelowAreAllMergedMergesOnlyItself() throws Exception {
        PullRequestDetails stacked = stacked(new PullRequestStack(
                9,
                2,
                2,
                List.of(new PullRequestStack.Entry(1, 7, State.MERGED), new PullRequestStack.Entry(2, 8, State.OPEN))));

        String screen = screen(
                PullRequestPopup.loading(new BranchName("support_rename"), PR).with(Either.right(stacked)));

        assertTrue(screen.contains("    Stack  #9 · 2 of 2"), screen);
        assertFalse(screen.contains("merging also merges"), screen);
        assertTrue(screen.contains("(s) Squash and merge "), screen);
        assertFalse(screen.contains("PRs"), screen);
    }

    /**
     * The ready pull request, read as sitting in {@code stack}.
     */
    private static PullRequestDetails stacked(PullRequestStack stack) {
        return new PullRequestDetails(
                READY.number(),
                READY.title(),
                READY.author(),
                READY.state(),
                READY.draft(),
                READY.headBranch(),
                READY.baseBranch(),
                READY.createdAt(),
                READY.pushedAt(),
                READY.additions(),
                READY.deletions(),
                READY.changedFiles(),
                READY.commits(),
                READY.mergeable(),
                READY.review(),
                READY.comments(),
                READY.reviewThreads(),
                READY.labels(),
                READY.reviewers(),
                READY.checks(),
                "abc123",
                Option.of(stack),
                Option.none(),
                "");
    }

    @Test
    void theBottomOfAStackMergesOnlyItself() throws Exception {
        PullRequest bottom = new PullRequest(
                42,
                "support_rename",
                false,
                Checks.PASSING,
                Review.APPROVED,
                Option.of(new PullRequestStack(
                        96,
                        1,
                        2,
                        List.of(
                                new PullRequestStack.Entry(1, 42, State.OPEN),
                                new PullRequestStack.Entry(2, 43, State.OPEN)))));

        String screen = screen(PullRequestPopup.loading(new BranchName("support_rename"), bottom)
                .with(Either.right(READY)));

        // The details read without a stack say it is not stacked anymore.
        assertFalse(screen.contains("Stack"), screen);
        assertTrue(screen.contains("(s) Squash and merge "), screen);
        assertFalse(screen.contains("PRs"), screen);
    }

    @Test
    void showsTheDetailsAsLabelledRows() throws Exception {
        String screen = screen(
                PullRequestPopup.loading(new BranchName("support_rename"), PR).with(Either.right(DETAILS)));

        assertTrue(screen.contains("Add rename support to the files panel"), screen);
        assertTrue(screen.contains("    State  Open · Draft"), screen);
        assertTrue(screen.contains("   Author  @cvf"), screen);
        assertTrue(screen.contains("   Branch  support_rename → main"), screen);
        assertTrue(screen.contains("  Created  2026-10-10 (3d ago)"), screen);
        assertTrue(screen.contains("   Pushed  2026-10-12 (1d ago)"), screen);
        assertTrue(screen.contains("  Changes  +120 −34 in 7 files, 3 commits"), screen);
        assertTrue(screen.contains("Mergeable  yes"), screen);
        assertTrue(screen.contains("   Review  👎 changes requested"), screen);
        assertTrue(screen.contains("Reviewers  👍 @alice  👎 @bob  ? @dave  💬 @carol"), screen);
        assertTrue(screen.contains(" Comments  5"), screen);
        assertTrue(screen.contains("  Threads  5 with 12 comments, 2 unresolved"), screen);
        assertTrue(screen.contains("   Labels  enhancement, ui"), screen);
        assertTrue(screen.contains("   Checks  ❌ 1 failed · ⏳ 1 running · ✅ 1 passed"), screen);
        // The failed check first, then the running one; the passed one is only counted.
        assertTrue(screen.indexOf("❌ build") < screen.indexOf("⏳ integration"), screen);
        assertFalse(screen.contains("✅ lint"), screen);
        assertTrue(screen.contains("(enter) select   (esc) cancel"), screen);
    }

    @Test
    void saysHowFarTheBaseMovedOnWhileStillMergeable() throws Exception {
        String behind = screen(PullRequestPopup.loading(new BranchName("support_rename"), PR)
                .with(Either.right(DETAILS.withBehindBase(3))));
        String upToDate = screen(PullRequestPopup.loading(new BranchName("support_rename"), PR)
                .with(Either.right(DETAILS.withBehindBase(0))));

        assertTrue(behind.contains("   Branch  support_rename → main · 3 behind"), behind);
        assertTrue(behind.contains("Mergeable  yes"), behind);
        assertFalse(upToDate.contains("behind"), upToDate);
    }

    @Test
    void aRefreshThatCouldNotTellHowFarBehindKeepsTheCountForTheSameCommitOnTheSameBase() throws Exception {
        PullRequestPopup shown = PullRequestPopup.loading(new BranchName("support_rename"), PR)
                .with(Either.right(at("abc123", "main").withBehindBase(3)));

        String same = screen(shown.with(Either.right(at("abc123", "main"))));
        String pushed = screen(shown.with(Either.right(at("def456", "main"))));
        String retargeted = screen(shown.with(Either.right(at("abc123", "develop"))));
        String upToDate = screen(shown.with(Either.right(at("abc123", "main").withBehindBase(0))));

        assertTrue(same.contains("support_rename → main · 3 behind"), same);
        assertFalse(pushed.contains("behind"), pushed);
        assertFalse(retargeted.contains("behind"), retargeted);
        assertFalse(upToDate.contains("behind"), upToDate);
    }

    @Test
    void countsOneFileAndOneCommitInTheSingular() throws Exception {
        String screen = screen(
                PullRequestPopup.loading(new BranchName("support_rename"), PR).with(Either.right(READY)));

        assertTrue(screen.contains("  Changes  +1 −1 in 1 file, 1 commit"), screen);
    }

    // As a change and its revert leave, or only empty commits.
    @Test
    void countsNoFilesAndNoCommitsInThePlural() throws Exception {
        PullRequestDetails empty = new PullRequestDetails(
                READY.number(),
                READY.title(),
                READY.author(),
                READY.state(),
                READY.draft(),
                READY.headBranch(),
                READY.baseBranch(),
                READY.createdAt(),
                READY.pushedAt(),
                0,
                0,
                0,
                0,
                READY.mergeable(),
                READY.review(),
                READY.comments(),
                READY.reviewThreads(),
                READY.labels(),
                READY.reviewers(),
                READY.checks());

        String screen = screen(
                PullRequestPopup.loading(new BranchName("support_rename"), PR).with(Either.right(empty)));

        assertTrue(screen.contains("  Changes  +0 −0 in 0 files, 0 commits"), screen);
    }

    @Test
    void countsNoReviewThreads() throws Exception {
        String screen = screen(
                PullRequestPopup.loading(new BranchName("support_rename"), PR).with(Either.right(READY)));

        assertTrue(screen.contains("  Threads  0 "), screen);
    }

    @Test
    void saysWhenAllReviewThreadsAreResolved() throws Exception {
        String screen = screen(PullRequestPopup.loading(new BranchName("support_rename"), PR)
                .with(Either.right(readyWith(new ReviewThreads(1, 1, 0, 1)))));

        assertTrue(screen.contains("  Threads  1 with 1 comment, all resolved"), screen);
    }

    @Test
    void doesNotSayAllAreResolvedWhenNotAllThreadsWereRead() throws Exception {
        String screen = screen(PullRequestPopup.loading(new BranchName("support_rename"), PR)
                .with(Either.right(readyWith(new ReviewThreads(130, 100, 0, 180)))));

        assertTrue(screen.contains("  Threads  130 with 180 comments "), screen);
        assertFalse(screen.contains("resolved"), screen);
    }

    private static PullRequestDetails readyWith(ReviewThreads threads) {
        return new PullRequestDetails(
                READY.number(),
                READY.title(),
                READY.author(),
                READY.state(),
                READY.draft(),
                READY.headBranch(),
                READY.baseBranch(),
                READY.createdAt(),
                READY.createdAt(),
                READY.additions(),
                READY.deletions(),
                READY.changedFiles(),
                READY.commits(),
                READY.mergeable(),
                READY.review(),
                READY.comments(),
                threads,
                READY.labels(),
                READY.reviewers(),
                READY.checks());
    }

    @Test
    void aTitleOfWideCharactersGetsTheLinesItTakes() throws Exception {
        // 81 characters of two columns each: three lines of the 76 columns inside the dialog.
        String title = "変更".repeat(39) + "終わり";
        PullRequestDetails wide = new PullRequestDetails(
                DETAILS.number(),
                title,
                DETAILS.author(),
                DETAILS.state(),
                DETAILS.draft(),
                DETAILS.headBranch(),
                DETAILS.baseBranch(),
                DETAILS.createdAt(),
                DETAILS.createdAt(),
                DETAILS.additions(),
                DETAILS.deletions(),
                DETAILS.changedFiles(),
                DETAILS.commits(),
                DETAILS.mergeable(),
                DETAILS.review(),
                DETAILS.comments(),
                DETAILS.reviewThreads(),
                DETAILS.labels(),
                DETAILS.reviewers(),
                DETAILS.checks());

        String screen = screen(
                PullRequestPopup.loading(new BranchName("support_rename"), PR).with(Either.right(wide)));

        assertTrue(screen.contains("終わり"), screen);
        assertTrue(screen.contains("    State  Open · Draft"), screen);
    }

    @Test
    void manyChecksThatDidNotPassAreCut() throws Exception {
        List<Check> checks = List.range(0, 11).map(i -> new Check("check" + i, Checks.FAILING));
        PullRequestDetails details = new PullRequestDetails(
                42,
                "t",
                "",
                State.OPEN,
                false,
                "a",
                "b",
                NOW,
                NOW,
                0,
                0,
                0,
                1,
                Mergeable.UNKNOWN,
                Review.NONE,
                0,
                ReviewThreads.NONE,
                List.empty(),
                List.empty(),
                checks);

        String screen = screen(PullRequestPopup.loading(new BranchName("a"), PR).with(Either.right(details)));

        assertTrue(screen.contains("check7"), screen);
        assertFalse(screen.contains("check8"), screen);
        assertTrue(screen.contains("… 3 more"), screen);
        assertFalse(screen.contains("Author"), screen);
        assertFalse(screen.contains("Reviewers"), screen);
        assertFalse(screen.contains("Labels"), screen);
    }

    @Test
    void saysHowLongTheChecksRunAndAreExpectedToTakeStill() throws Exception {
        String screen = timedScreen(
                List.of(
                        timed("verify", 120, Option.of(30)),
                        timed("acceptance", 120, Option.none()),
                        timed("package", 120, Option.of(118)),
                        new Check("ci/status", Checks.PENDING)),
                learned());

        // verify and the status are not known, so neither is when they are all done.
        assertTrue(screen.contains("Checks  ⏳ 4 running · done in ?"), screen);
        assertTrue(screen.contains("⏳ package     running 2m · <5s left"), screen);
        assertTrue(screen.contains("⏳ verify      running 30s · ? left"), screen);
        assertTrue(screen.contains("⏳ ci/status   running"), screen);
        assertTrue(screen.contains("💤 acceptance  queued · ~2m 15s left"), screen);
        // Running before queued, the soonest done first, those not known last.
        assertTrue(screen.indexOf("package") < screen.indexOf("verify"), screen);
        assertTrue(screen.indexOf("ci/status") < screen.indexOf("acceptance"), screen);
    }

    @Test
    void aQueuedCheckIsDimmedAllAlongARunningOneOnlyInItsTime() {
        List<Check> all = List.of(timed("acceptance", 120, Option.none()), timed("package", 120, Option.of(118)));

        // After the symbol, the name and how it is doing.
        List<StyledElement<?>> queued = CheckRows.row(all.head(), all, Option.of(learned()), NOW, 10, false)
                .tail();
        List<StyledElement<?>> running = CheckRows.row(all.last(), all, Option.of(learned()), NOW, 10, false)
                .tail();

        assertTrue(queued.forAll(piece -> piece.cssClasses().contains(Theme.Classes.DIM)));
        assertFalse(running.head().cssClasses().contains(Theme.Classes.DIM));
        assertTrue(running.last().cssClasses().contains(Theme.Classes.DIM));
    }

    @Test
    void aLongFailedNameDoesNotPushTheTimesOfTheOthersOut() throws Exception {
        String screen = timedScreen(
                List.of(new Check("integration-tests-windows", Checks.FAILING), timed("verify", 120, Option.of(30))),
                learned());

        assertTrue(screen.contains("❌ integration-tests-windows"), screen);
        assertTrue(screen.contains("⏳ verify  running 30s · ? left"), screen);
    }

    @Test
    void saysWhenTheChecksAreExpectedToBeDoneTogether() throws Exception {
        String screen = timedScreen(
                List.of(timed("package", 120, Option.of(118)), timed("acceptance", 120, Option.none())), learned());

        assertTrue(screen.contains("Checks  ⏳ 2 running · done in ~2m"), screen);
    }

    @Test
    void aCheckPastItsEstimateIsOverdue() throws Exception {
        String screen = timedScreen(List.of(timed("acceptance", 500, Option.of(400))), learned());

        assertTrue(screen.contains("Checks  ⏳ 1 running · taking longer than usual"), screen);
        assertTrue(screen.contains("⏳ acceptance  running 6m 40s · overdue"), screen);
    }

    @Test
    void whileTheChecksThatRanLatelyAreReadEstimatesAreComing() throws Exception {
        String screen = timedScreen(
                List.of(timed("verify", 120, Option.of(30))), learned().withTopUp(NOW));

        assertTrue(screen.contains("Checks  ⏳ 1 running · done in …"), screen);
        assertTrue(screen.contains("⏳ verify  running 30s · … left"), screen);
    }

    @Test
    void tellsWhyTheDetailsCouldNotBeRead() throws Exception {
        String screen = screen(PullRequestPopup.loading(new BranchName("support_rename"), PR)
                .with(Either.left(refused("not logged in"))));

        assertTrue(screen.contains("Could not read the pull request: not logged in"), screen);
        assertTrue(screen.contains("(enter) select   (esc) cancel"), screen);
    }

    @Test
    void keepsItsSizeWhenTheDetailsArrive() throws Exception {
        List<Check> checks = List.range(0, 12).map(i -> new Check("check" + i, Checks.FAILING));
        PullRequestDetails fullest = new PullRequestDetails(
                42,
                "A title long enough to need more than three lines at the width of the dialog, so that it must be "
                        + "cut to keep the dialog from growing, which takes quite a few words when the dialog is "
                        + "as wide as it is, so here are some more of them, and then a few more still, to be sure "
                        + "that it really does not fit",
                "cvf",
                State.OPEN,
                false,
                "a",
                "b",
                NOW,
                NOW,
                0,
                0,
                0,
                1,
                Mergeable.UNKNOWN,
                Review.APPROVED,
                0,
                ReviewThreads.NONE,
                List.of("ui"),
                DETAILS.reviewers(),
                checks);
        PullRequestPopup loading = PullRequestPopup.loading(new BranchName("support_rename"), PR);

        int hintLine = hintLine(screen(loading));

        assertEquals(hintLine, hintLine(screen(loading.with(Either.right(DETAILS)))));
        String fullestScreen = screen(loading.with(Either.right(fullest)));
        assertEquals(hintLine, hintLine(fullestScreen));
        // The last action still fits under the tallest details.
        assertTrue(fullestScreen.contains("(R) Refresh"), fullestScreen);
        assertEquals(hintLine, hintLine(screen(loading.with(Either.left(refused("offline"))))));
    }

    @Test
    void listsWhatCanBeDoneWithThePullRequest() throws Exception {
        String screen = screen(
                PullRequestPopup.loading(new BranchName("support_rename"), PR).with(Either.right(READY)));

        assertTrue(screen.contains("(o) Open in browser"), screen);
        assertTrue(screen.contains("(d) Convert to draft"), screen);
        assertTrue(screen.contains("(m) Merge"), screen);
        assertTrue(screen.contains("(s) Squash and merge"), screen);
        assertTrue(screen.contains("(r) Rebase and merge"), screen);
        assertTrue(screen.contains("(x) Close pull request"), screen);
    }

    @Test
    void lettersMergeOrCloseAPullRequestThatIsReady() throws Exception {
        Model model = TestModels.loaded()
                .openPopup(PullRequestPopup.loading(new BranchName("support_rename"), PR)
                        .with(Either.right(READY)));
        try (ToolkitTestRunner runner = start(model)) {
            for (char key : new char[] {'d', 'm', 's', 'r', 'x', 'o'}) {
                SettledPilot.of(runner).press(key);
                SettledPilot.of(runner).pause();
            }
        }

        assertEquals(
                java.util.List.of(
                        new PullRequestMsg.DraftChosen(),
                        new PullRequestMsg.MergeChosen(MergeMethod.MERGE),
                        new PullRequestMsg.MergeChosen(MergeMethod.SQUASH),
                        new PullRequestMsg.MergeChosen(MergeMethod.REBASE),
                        new PullRequestMsg.CloseChosen(),
                        new PullRequestMsg.OpenConfirmed()),
                dispatched);
    }

    @Test
    void aDraftCanBeMarkedReadyOrClosedButNotMergedAndNothingIsDoneBeforeTheDetailsAreRead() throws Exception {
        PullRequestPopup loading = PullRequestPopup.loading(new BranchName("support_rename"), PR);
        for (PullRequestPopup popup : List.of(loading, loading.with(Either.right(DETAILS)))) {
            try (ToolkitTestRunner runner = start(TestModels.loaded().openPopup(popup))) {
                for (char key : new char[] {'d', 'm', 'x'}) {
                    SettledPilot.of(runner).press(key);
                    SettledPilot.of(runner).pause();
                }
            }
        }

        // DETAILS is a draft.
        assertEquals(java.util.List.of(new PullRequestMsg.DraftChosen(), new PullRequestMsg.CloseChosen()), dispatched);
    }

    @Test
    void theCommentsAreWrittenAndViewedOnceTheDetailsAreRead() throws Exception {
        PullRequestPopup loading = PullRequestPopup.loading(new BranchName("support_rename"), PR);
        for (PullRequestPopup popup : List.of(loading, loading.with(Either.right(READY)))) {
            try (ToolkitTestRunner runner = start(TestModels.loaded().openPopup(popup))) {
                for (char key : new char[] {'c', 'v'}) {
                    SettledPilot.of(runner).press(key);
                    SettledPilot.of(runner).pause();
                }
            }
        }

        assertEquals(java.util.List.of(new CommentMsg.Requested(), new CommentsMsg.Requested()), dispatched);
    }

    @Test
    void aMergedOrClosedPullRequestIsNeitherChangedMergedNorClosed() throws Exception {
        for (State state : List.of(State.MERGED, State.CLOSED)) {
            PullRequestPopup popup = PullRequestPopup.loading(new BranchName("support_rename"), PR)
                    .with(Either.right(readyIn(state)));
            try (ToolkitTestRunner runner = start(TestModels.loaded().openPopup(popup))) {
                for (char key : new char[] {'d', 'm', 's', 'r', 'x'}) {
                    SettledPilot.of(runner).press(key);
                    SettledPilot.of(runner).pause();
                }
            }
        }

        assertEquals(java.util.List.of(), dispatched);
    }

    private static PullRequestDetails readyIn(State state) {
        return new PullRequestDetails(
                READY.number(),
                READY.title(),
                READY.author(),
                state,
                READY.draft(),
                READY.headBranch(),
                READY.baseBranch(),
                READY.createdAt(),
                READY.createdAt(),
                READY.additions(),
                READY.deletions(),
                READY.changedFiles(),
                READY.commits(),
                READY.mergeable(),
                READY.review(),
                READY.comments(),
                READY.reviewThreads(),
                READY.labels(),
                READY.reviewers(),
                READY.checks());
    }

    @Test
    void aDraftOffersToMarkItReadyForReview() throws Exception {
        String screen = screen(
                PullRequestPopup.loading(new BranchName("support_rename"), PR).with(Either.right(DETAILS)));

        assertTrue(screen.contains("(d) Mark ready for review"), screen);
    }

    @Test
    void enterOpensInTheBrowserAndEscapeCloses() throws Exception {
        Model model = TestModels.loaded().openPopup(PullRequestPopup.loading(new BranchName("support_rename"), PR));
        try (ToolkitTestRunner runner = start(model)) {
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(
                java.util.List.of(new PullRequestMsg.OpenConfirmed(), new PullRequestMsg.InfoClosed()), dispatched);
    }

    private String screen(PullRequestPopup popup) throws Exception {
        return screen(popup, model -> model);
    }

    private String screen(PullRequestPopup popup, UnaryOperator<Model> change) throws Exception {
        Model model = change.apply(TestModels.loaded().withNow(NOW)).openPopup(popup);
        try (ToolkitTestRunner runner = start(model)) {
            // About a terminal filling a 1920x1080 screen.
            return RenderedText.ofSize(runner, () -> view(model), 120, 50);
        }
    }

    private ToolkitTestRunner start(Model model) throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(model));
        SettledPilot.focus(runner, PullRequestDialog.ID);
        RenderedText.of(runner, () -> view(model));
        return runner;
    }

    private Element view(Model model) {
        return stack(text("background"), dialog.render(model).get());
    }
}
