package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.git.model.Tracking;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.git.model.Sha;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.branch.NewBranchMsg;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The highlighted row of the branches pane follows its branch when the list is reordered.
 */
class BranchesPanelTest {

    private static final Branch MAIN = new Branch("main", true, new Sha("1"));
    private static final Branch FEATURE = new Branch("feature", false, new Sha("2"));
    private static final Branch OLD = new Branch("old", false, new Sha("3"));

    private final BranchesPanel panel = new BranchesPanel(msg -> { });
    private final Model before = with(TestModels.loaded(new SampleData()), List.of(MAIN, FEATURE, OLD));

    @Test
    void afterACheckoutTheNewCurrentBranchIsHighlighted() throws Exception {
        Model after = with(before, List.of(new Branch("old", true, new Sha("3")), new Branch("main", false, new Sha("1")), FEATURE));

        assertEquals("old", highlightedAfterMovingTo(2, after));
    }

    @Test
    void aReorderingWithoutCheckoutKeepsTheSameBranchHighlighted() throws Exception {
        Model after = with(before, List.of(MAIN, OLD, FEATURE));

        assertEquals("feature", highlightedAfterMovingTo(1, after));
    }

    @Test
    void aPullRequestShowsItsNumberItsChecksAndItsReview() {
        assertEquals(List.of(), BranchesPanel.pullRequest(Option.none()));
        assertEquals(List.of(LoadableList.Seg.styled(" #42", Theme.Classes.PULL_REQUEST),
                        LoadableList.Seg.styled(" ✓", Theme.Classes.CHECKS_PASSED),
                        LoadableList.Seg.styled(" ✔", Theme.Classes.REVIEW_APPROVED)),
                BranchesPanel.pullRequest(Option.of(new PullRequest(42, "b", false, Checks.PASSING, Review.APPROVED))));
        assertEquals(List.of(LoadableList.Seg.styled(" #40", Theme.Classes.PULL_REQUEST),
                        LoadableList.Seg.styled(" ✗", Theme.Classes.CHECKS_FAILED),
                        LoadableList.Seg.styled(" ?", Theme.Classes.DIM)),
                BranchesPanel.pullRequest(Option.of(new PullRequest(40, "b", false, Checks.FAILING, Review.REQUIRED))));
        assertEquals(List.of(LoadableList.Seg.styled(" #38", Theme.Classes.PULL_REQUEST),
                        LoadableList.Seg.styled(" ●", Theme.Classes.CHECKS_RUNNING),
                        LoadableList.Seg.styled(" ✎", Theme.Classes.REVIEW_CHANGES)),
                BranchesPanel.pullRequest(Option.of(new PullRequest(38, "b", false, Checks.PENDING, Review.CHANGES_REQUESTED))));
        // A draft without checks or reviews shows only its number, dimmed.
        assertEquals(List.of(LoadableList.Seg.styled(" #37", Theme.Classes.DIM)),
                BranchesPanel.pullRequest(Option.of(new PullRequest(37, "b", true, Checks.NONE, Review.NONE))));
    }

    @Test
    void aBranchWhoseUpstreamIsGoneSaysSo() {
        assertEquals(List.of(LoadableList.Seg.styled(" gone", Theme.Classes.GONE)),
                BranchesPanel.sync(new Branch("b", false, new Sha("1"), java.time.Instant.EPOCH, false, false,
                        new Tracking.Gone(new RemoteBranch("origin", "b")))));
        // A branch that was never pushed shows nothing.
        assertEquals(List.of(), BranchesPanel.sync(new Branch("b", false, new Sha("1"))));
    }

    @Test
    void syncShowsAnAheadArrowForUnpushedAndABehindOneForIncomingCommits() {
        java.time.Instant t = java.time.Instant.EPOCH;
        assertEquals(List.of(), BranchesPanel.sync(new Branch("b", false, new Sha("1"), t, true, false, Option.of(new Upstream(new RemoteBranch("origin", "b"), 0, 0)))));
        assertEquals(List.of(LoadableList.Seg.styled(" ↑2", Theme.Classes.AHEAD)),
                BranchesPanel.sync(new Branch("b", false, new Sha("1"), t, true, false, Option.of(new Upstream(new RemoteBranch("origin", "b"), 2, 0)))));
        assertEquals(List.of(LoadableList.Seg.styled(" ↓1", Theme.Classes.BEHIND)),
                BranchesPanel.sync(new Branch("b", false, new Sha("1"), t, true, false, Option.of(new Upstream(new RemoteBranch("origin", "b"), 0, 1)))));
        assertEquals(List.of(LoadableList.Seg.styled(" ↑2", Theme.Classes.AHEAD),
                        LoadableList.Seg.styled(" ↓1", Theme.Classes.BEHIND)),
                BranchesPanel.sync(new Branch("b", false, new Sha("1"), t, true, false, Option.of(new Upstream(new RemoteBranch("origin", "b"), 2, 1)))));
    }

    @Test
    void spinnerFramesBounceAsTimePasses() {
        java.util.Set<String> frames = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> BranchesPanel.spinnerFrame(java.time.Instant.ofEpochMilli(i * 120L)))
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(java.util.Set.copyOf(java.util.Arrays.asList(dev.tamboui.widgets.spinner.SpinnerStyle.BOUNCING_BAR.frames())), frames);
    }

    @Test
    void aBranchBeingPushedOrPulledShowsWhatIsDoneLeftOfTheSpinner() throws Exception {
        Model busy = before.withBusyBranch("feature", Operation.PUSH).withBusyBranch("main", Operation.PULL);
        String frame = BranchesPanel.spinnerFrame(java.time.Instant.EPOCH);
        BranchesPanel panel = new BranchesPanel(msg -> { }, java.time.Clock.fixed(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(busy, BranchesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> panel.render(busy, BranchesPanel.ID));
            assertEquals(true, screen.contains("feature Pushing " + frame), screen);
            assertEquals(true, screen.contains("main Pulling " + frame), screen);
        }
    }

    @Test
    void aBranchBeingDeletedShowsOnlyTheSpinner() throws Exception {
        Model deleting = before.withBusyBranch("feature", Operation.DELETE_BRANCH);
        String frame = BranchesPanel.spinnerFrame(java.time.Instant.EPOCH);
        BranchesPanel panel = new BranchesPanel(msg -> { }, java.time.Clock.fixed(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(deleting, BranchesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> panel.render(deleting, BranchesPanel.ID));
            assertEquals(true, screen.contains("feature " + frame), screen);
            assertEquals(false, screen.contains("main " + frame), screen);
        }
    }

    @Test
    void aFastForwardIsShownAsAPull() {
        assertEquals(BranchesPanel.busyLabel(Operation.PULL), BranchesPanel.busyLabel(Operation.FAST_FORWARD));
    }

    @Test
    void ageIsShownInTheLargestFittingUnit() {
        assertEquals("45s", BranchesPanel.age(java.time.Duration.ofSeconds(45)));
        assertEquals("2m", BranchesPanel.age(java.time.Duration.ofSeconds(150)));
        assertEquals("5m", BranchesPanel.age(java.time.Duration.ofMinutes(5)));
        assertEquals("3h", BranchesPanel.age(java.time.Duration.ofHours(3)));
        assertEquals("3h", BranchesPanel.age(java.time.Duration.ofMinutes(181)));
        assertEquals("3h", BranchesPanel.age(java.time.Duration.ofMinutes(210)));
        assertEquals("3h", BranchesPanel.age(java.time.Duration.ofMinutes(239)));
        assertEquals("4h", BranchesPanel.age(java.time.Duration.ofMinutes(240)));
        assertEquals("2d", BranchesPanel.age(java.time.Duration.ofDays(2)));
    }

    @Test
    void nAsksForANewBranchStartingAtTheHighlightedBranch() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.ID))) {
            testRunner.runner().focusManager().setFocus(BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.ID));
            testRunner.pilot().press(KeyCode.DOWN);
            testRunner.pilot().press('n');
            testRunner.pilot().pause();
            testRunner.pilot().quit();
        }

        assertEquals(java.util.List.of(new NewBranchMsg.Requested("feature")), dispatched);
    }

    @Test
    void shiftPPushesTheHighlightedBranch() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.ID))) {
            testRunner.runner().focusManager().setFocus(BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.ID));
            testRunner.pilot().press(KeyCode.DOWN);
            testRunner.pilot().press('P');
            testRunner.pilot().pause();
            testRunner.pilot().quit();
        }

        assertEquals(java.util.List.of(new net.vanfleteren.jazygit.feature.branch.PushMsg.Requested("feature")), dispatched);
    }

    @Test
    void pPullsTheHighlightedBranch() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.ID))) {
            testRunner.runner().focusManager().setFocus(BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.ID));
            testRunner.pilot().press(KeyCode.DOWN);
            testRunner.pilot().press('p');
            testRunner.pilot().pause();
            testRunner.pilot().quit();
        }

        assertEquals(java.util.List.of(new net.vanfleteren.jazygit.feature.branch.PullMsg.Requested("feature")), dispatched);
    }

    @Test
    void questionMarkAsksForTheKeybindingsOfThePanel() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.ID))) {
            testRunner.runner().focusManager().setFocus(BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.ID));
            testRunner.pilot().press('?');
            testRunner.pilot().pause();
            testRunner.pilot().quit();
        }

        assertEquals(java.util.List.of(new net.vanfleteren.jazygit.feature.help.HelpMsg.Requested(
                net.vanfleteren.jazygit.keys.Scopes.BRANCHES.id())), dispatched);
    }

    private String highlightedAfterMovingTo(int index, Model after) throws Exception {
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.ID))) {
            testRunner.runner().focusManager().setFocus(BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.ID));
            for (int i = 0; i < index; i++) {
                testRunner.pilot().press(KeyCode.DOWN);
            }
            testRunner.pilot().pause();
            assertEquals(index, panel.selectedIndex());

            RenderedText.of(testRunner, () -> panel.render(after, BranchesPanel.ID));
            testRunner.pilot().quit();
            return panel.selectedBranch().get();
        }
    }

    private static Model with(Model model, List<Branch> branches) {
        return Update.update(model, new LoadMsg.BranchesLoaded(branches)).model();
    }
}
