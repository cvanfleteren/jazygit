package net.vanfleteren.jazygit.features.forgejo.pulls;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.FEATURE;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.LISTED;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.ORIGIN;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.TIME;
import static net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestFixtures.details;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import io.vavr.collection.LinkedHashSet;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.forgejo.ForgejoError;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMergeSettings;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMergeStyle;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequestDetails;
import net.vanfleteren.jazygit.core.forgejo.ForgejoReview.Verdict;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import org.junit.jupiter.api.Test;

/**
 * The dialog of a pull request on Forgejo, as GitHub's draws its own (ADR 175, 176).
 */
class ForgejoPullRequestDialogTest {

    private static final CommitStatus BUILDS = new CommitStatus(
            CommitStatus.State.FAILED,
            List.of(
                    new CommitStatus.Status("check / lint (push)", CommitStatus.State.FAILED, "Failing after 1m"),
                    new CommitStatus.Status("check / test (push)", CommitStatus.State.RUNNING, ""),
                    new CommitStatus.Status("check / build (push)", CommitStatus.State.PASSED, "")));

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final ForgejoPullRequestDialog dialog =
            new ForgejoPullRequestDialog(dispatched::add, Clock.fixed(TIME, ZoneOffset.UTC));

    private static ForgejoPullRequestPopup read(ShownPullRequest shown) {
        return ForgejoPullRequestPopup.loading(FEATURE.branch(), ORIGIN, FEATURE, LISTED, 1)
                .with(Either.right(shown), 1);
    }

    @Test
    void whileItIsReadItShowsItsBranchAndThatItIsRead() throws Exception {
        String screen = screen(ForgejoPullRequestPopup.loading(FEATURE.branch(), ORIGIN, FEATURE, LISTED, 1));

        assertTrue(screen.contains("Pull request #12"), screen);
        assertTrue(screen.contains("Branch  feature"), screen);
        assertTrue(screen.contains("Reading the pull request from Forgejo…"), screen);
    }

    @Test
    void itShowsThePullRequestAsLabelledRows() throws Exception {
        String screen = screen(
                read(new ShownPullRequest(details("Show the pull request"), Option.of(Verdict.APPROVED), BUILDS)));

        assertTrue(screen.contains("Show the pull request"), screen);
        assertTrue(screen.contains("      State  Open"), screen);
        assertTrue(screen.contains("     Author  @cvf"), screen);
        assertTrue(screen.contains("     Branch  feature → main"), screen);
        assertTrue(screen.contains("    Created  2026-11-19 (2d ago)"), screen);
        assertTrue(screen.contains("    Updated  2026-11-21 (1h ago)"), screen);
        assertTrue(screen.contains("    Changes  +120 −7 in 5 files"), screen);
        assertTrue(screen.contains("  Mergeable  yes"), screen);
        assertTrue(screen.contains("     Review  + approved"), screen);
        assertTrue(screen.contains("  Reviewers  ? @anna"), screen);
        assertTrue(screen.contains("   Comments  4"), screen);
        assertTrue(screen.contains("     Labels  feature"), screen);
        assertTrue(screen.contains("     Checks  ✗ 1 failed · ● 1 running · ✓ 1 passed"), screen);
        assertTrue(screen.contains("✗ check / lint (push)"), screen);
        assertTrue(screen.contains("● check / test (push)"), screen);
        assertFalse(screen.contains("check / build (push)"), screen);
        assertTrue(screen.contains("(o) Open in browser"), screen);
        assertTrue(screen.contains("(R) Refresh"), screen);
    }

    @Test
    void whetherItMergesShowsOnlyForAnOpenPullRequestThatIsNoDraftAndNeverAsConflicts() throws Exception {
        // Forgejo says false for a draft, while it checks, and for one merged or closed: no conflict known.
        String notNow = screen(read(shown(ForgejoPullRequestDetails.State.OPEN, false, false)));
        assertTrue(notNow.contains("  Mergeable  no"), notNow);
        assertFalse(notNow.contains("conflicts"), notNow);

        String draft = screen(read(shown(ForgejoPullRequestDetails.State.OPEN, true, false)));
        assertTrue(draft.contains("Open · Draft"), draft);
        assertFalse(draft.contains("Mergeable"), draft);

        String merged = screen(read(shown(ForgejoPullRequestDetails.State.MERGED, false, false)));
        assertTrue(merged.contains("      State  Merged"), merged);
        assertFalse(merged.contains("Mergeable"), merged);
    }

    private static ShownPullRequest shown(ForgejoPullRequestDetails.State state, boolean draft, boolean mergeable) {
        ForgejoPullRequestDetails pr = details("Shown");
        return new ShownPullRequest(
                new ForgejoPullRequestDetails(
                        pr.number(),
                        pr.title(),
                        pr.author(),
                        new ForgejoPullRequestDetails.Status(state, draft, mergeable),
                        pr.branches(),
                        pr.activity(),
                        pr.size(),
                        pr.conversation(),
                        pr.url()),
                Option.none(),
                CommitStatus.NONE);
    }

    @Test
    void withoutBuildsOrAReviewItSaysSo() throws Exception {
        String screen = screen(read(new ShownPullRequest(details("Quiet"), Option.none(), CommitStatus.NONE)));

        assertTrue(screen.contains("     Review  none yet"), screen);
        assertTrue(screen.contains("     Checks  none"), screen);
    }

    @Test
    void aReadThatFailedSaysWhyAndOneAgainThatFailedMarksItStale() throws Exception {
        ForgejoFailure down = new ForgejoFailure.Asked(new ForgejoError.Unreachable("timed out"));
        String failed = screen(ForgejoPullRequestPopup.loading(FEATURE.branch(), ORIGIN, FEATURE, LISTED, 1)
                .with(Either.left(down), 1));
        assertTrue(failed.contains("Could not read the pull request: Forgejo could not be reached: timed out"), failed);

        String stale = screen(read(new ShownPullRequest(details("Shown"), Option.none(), CommitStatus.NONE))
                .readingAgain(2)
                .with(Either.left(down), 2));
        assertTrue(stale.contains("Pull request #12 · not up to date"), stale);
        assertTrue(stale.contains("Shown"), stale);
    }

    @Test
    void itKeepsItsHeightWhenTheDetailsArrive() throws Exception {
        String loading = screen(ForgejoPullRequestPopup.loading(FEATURE.branch(), ORIGIN, FEATURE, LISTED, 1));
        String shown = screen(read(new ShownPullRequest(details("Shown"), Option.none(), BUILDS)));

        assertEquals(height(loading), height(shown));
    }

    private static long height(String screen) {
        return List.of(screen.split("\n")).count(line -> line.contains("│"));
    }

    @Test
    void itOffersForgejosFiveStylesOfMerging() throws Exception {
        String screen = screen(read(new ShownPullRequest(details("Merge it"), Option.none(), CommitStatus.NONE)));

        List.of(
                        "(m) Merge",
                        "(s) Squash and merge",
                        "(r) Rebase and merge",
                        "(M) Rebase, then merge commit",
                        "(f) Fast-forward only")
                .forEach(key -> assertTrue(screen.contains(key), screen));
    }

    @Test
    void aMergeKeyThatDoesNothingSaysWhy() {
        ForgejoPullRequestDetails pr = details("Merge it");
        ShownPullRequest merged = new ShownPullRequest(
                new ForgejoPullRequestDetails(
                        pr.number(),
                        pr.title(),
                        pr.author(),
                        new ForgejoPullRequestDetails.Status(ForgejoPullRequestDetails.State.MERGED, false, true),
                        pr.branches(),
                        pr.activity(),
                        pr.size(),
                        pr.conversation(),
                        pr.url()),
                Option.none(),
                CommitStatus.NONE);
        ShownPullRequest squashOnly = new ShownPullRequest(
                pr,
                Option.none(),
                CommitStatus.NONE,
                Option.of(new ForgejoMergeSettings(LinkedHashSet.of(ForgejoMergeStyle.SQUASH), false)));

        assertEquals(new MergeKey.Disabled(new MergeKey.Why.NotOpen()), MergeKey.of(merged, ForgejoMergeStyle.SQUASH));
        assertEquals(
                new MergeKey.Disabled(new MergeKey.Why.NotAllowed(ForgejoMergeStyle.REBASE)),
                MergeKey.of(squashOnly, ForgejoMergeStyle.REBASE));
        assertEquals(new MergeKey.Now(), MergeKey.of(squashOnly, ForgejoMergeStyle.SQUASH));
        assertFalse(MergeUpdate.choosable(read(merged), ForgejoMergeStyle.SQUASH));
        assertTrue(MergeUpdate.choosable(read(squashOnly), ForgejoMergeStyle.SQUASH));
    }

    @Test
    void itsSpinnerTurnsWhileItIsRead() {
        Model loading = TestModels.loaded()
                .openPopup(ForgejoPullRequestPopup.loading(FEATURE.branch(), ORIGIN, FEATURE, LISTED, 1));
        Model shown = TestModels.loaded()
                .openPopup(read(new ShownPullRequest(details("Shown"), Option.none(), CommitStatus.NONE)));

        assertTrue(ForgejoPullRequestDialog.showsSpinner(loading));
        assertFalse(ForgejoPullRequestDialog.showsSpinner(shown));
        assertEquals(1, ForgejoPullRequestDialog.shownTimes(shown).size());
    }

    private String screen(ForgejoPullRequestPopup popup) throws Exception {
        Model model = TestModels.loaded().withNow(TIME).openPopup(popup);
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(model))) {
            SettledPilot.focus(runner, ForgejoPullRequestDialog.ID);
            return RenderedText.ofSize(runner, () -> view(model), 120, 50);
        }
    }

    private Element view(Model model) {
        return stack(text("background"), dialog.render(model).get());
    }
}
