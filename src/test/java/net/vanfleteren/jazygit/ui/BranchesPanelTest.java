package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Tracking;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.git.model.Sha;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.branch.NewBranchMsg;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.feature.branch.BranchesTabMsg;
import net.vanfleteren.jazygit.feature.branch.CheckoutMsg;
import net.vanfleteren.jazygit.git.model.RemoteRef;
import net.vanfleteren.jazygit.state.BranchesTab;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.SelectionMsg;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The highlighted row of the branches pane follows its branch when the list is reordered.
 */
class BranchesPanelTest {

    private static final Branch MAIN = new Branch(new Branch.Name("main"), true, new Sha("1"));
    private static final Branch FEATURE = new Branch(new Branch.Name("feature"), false, new Sha("2"));
    private static final Branch OLD = new Branch(new Branch.Name("old"), false, new Sha("3"));

    private final BranchesPanel panel = new BranchesPanel(msg -> { }, tab -> { });
    private final Model before = with(TestModels.loaded(new SampleData()), List.of(MAIN, FEATURE, OLD));

    @Test
    void afterACheckoutTheNewCurrentBranchIsHighlighted() throws Exception {
        Model after = with(before, List.of(new Branch(new Branch.Name("old"), true, new Sha("3")), new Branch(new Branch.Name("main"), false, new Sha("1")), FEATURE));

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
    void aBranchShowsItsPullRequestElseTheChecksOnItsTip() {
        PullRequest pr = new PullRequest(42, "b", false, Checks.NONE, Review.NONE);

        assertEquals(List.of(LoadableList.Seg.styled(" #42", Theme.Classes.PULL_REQUEST)),
                BranchesPanel.gitHub(Option.of(pr), Option.of(Checks.FAILING)));
        assertEquals(List.of(LoadableList.Seg.styled(" ✗", Theme.Classes.CHECKS_FAILED)),
                BranchesPanel.gitHub(Option.none(), Option.of(Checks.FAILING)));
        assertEquals(List.of(), BranchesPanel.gitHub(Option.none(), Option.of(Checks.NONE)));
        assertEquals(List.of(), BranchesPanel.gitHub(Option.none(), Option.none()));
    }

    @Test
    void aBranchWhoseUpstreamIsGoneSaysSo() {
        assertEquals(List.of(LoadableList.Seg.styled(" gone", Theme.Classes.GONE)),
                BranchesPanel.sync(new Branch(new Branch.Name("b"), false, new Sha("1"), java.time.Instant.EPOCH, false, false,
                        new Tracking.Gone(new RemoteBranch("origin", new Branch.Name("b"))))));
        // A branch that was never pushed shows nothing.
        assertEquals(List.of(), BranchesPanel.sync(new Branch(new Branch.Name("b"), false, new Sha("1"))));
    }

    @Test
    void syncShowsAnAheadArrowForUnpushedAndABehindOneForIncomingCommits() {
        java.time.Instant t = java.time.Instant.EPOCH;
        assertEquals(List.of(), BranchesPanel.sync(new Branch(new Branch.Name("b"), false, new Sha("1"), t, true, false, Option.of(new Upstream(new RemoteBranch("origin", new Branch.Name("b")), 0, 0)))));
        assertEquals(List.of(LoadableList.Seg.styled(" ↑2", Theme.Classes.AHEAD)),
                BranchesPanel.sync(new Branch(new Branch.Name("b"), false, new Sha("1"), t, true, false, Option.of(new Upstream(new RemoteBranch("origin", new Branch.Name("b")), 2, 0)))));
        assertEquals(List.of(LoadableList.Seg.styled(" ↓1", Theme.Classes.BEHIND)),
                BranchesPanel.sync(new Branch(new Branch.Name("b"), false, new Sha("1"), t, true, false, Option.of(new Upstream(new RemoteBranch("origin", new Branch.Name("b")), 0, 1)))));
        assertEquals(List.of(LoadableList.Seg.styled(" ↑2", Theme.Classes.AHEAD),
                        LoadableList.Seg.styled(" ↓1", Theme.Classes.BEHIND)),
                BranchesPanel.sync(new Branch(new Branch.Name("b"), false, new Sha("1"), t, true, false, Option.of(new Upstream(new RemoteBranch("origin", new Branch.Name("b")), 2, 1)))));
    }

    @Test
    void spinnerFramesBounceAsTimePasses() {
        java.util.Set<String> frames = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> net.vanfleteren.jazygit.ui.TimeText.spinnerFrame(java.time.Instant.ofEpochMilli(i * 120L)))
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(java.util.Set.copyOf(java.util.Arrays.asList(dev.tamboui.widgets.spinner.SpinnerStyle.BOUNCING_BAR.frames())), frames);
    }

    @Test
    void aBranchBeingPushedOrPulledShowsWhatIsDoneLeftOfTheSpinner() throws Exception {
        Model busy = before.withBusyBranch(new BranchRef.Local(new Branch.Name("feature")), Operation.PUSH).withBusyBranch(new BranchRef.Local(new Branch.Name("main")), Operation.PULL);
        String frame = net.vanfleteren.jazygit.ui.TimeText.spinnerFrame(java.time.Instant.EPOCH);
        BranchesPanel panel = new BranchesPanel(msg -> { }, tab -> { }, java.time.Clock.fixed(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(busy, BranchesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> panel.render(busy, BranchesPanel.ID));
            assertEquals(true, screen.contains("feature Pushing " + frame), screen);
            assertEquals(true, screen.contains("main Pulling " + frame), screen);
        }
    }

    @Test
    void aBranchBeingDeletedShowsOnlyTheSpinner() throws Exception {
        Model deleting = before.withBusyBranch(new BranchRef.Local(new Branch.Name("feature")), Operation.DELETE_BRANCH);
        String frame = net.vanfleteren.jazygit.ui.TimeText.spinnerFrame(java.time.Instant.EPOCH);
        BranchesPanel panel = new BranchesPanel(msg -> { }, tab -> { }, java.time.Clock.fixed(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC));
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
        assertEquals("45s", net.vanfleteren.jazygit.ui.TimeText.age(java.time.Duration.ofSeconds(45)));
        assertEquals("2m", net.vanfleteren.jazygit.ui.TimeText.age(java.time.Duration.ofSeconds(150)));
        assertEquals("5m", net.vanfleteren.jazygit.ui.TimeText.age(java.time.Duration.ofMinutes(5)));
        assertEquals("3h", net.vanfleteren.jazygit.ui.TimeText.age(java.time.Duration.ofHours(3)));
        assertEquals("3h", net.vanfleteren.jazygit.ui.TimeText.age(java.time.Duration.ofMinutes(181)));
        assertEquals("3h", net.vanfleteren.jazygit.ui.TimeText.age(java.time.Duration.ofMinutes(210)));
        assertEquals("3h", net.vanfleteren.jazygit.ui.TimeText.age(java.time.Duration.ofMinutes(239)));
        assertEquals("4h", net.vanfleteren.jazygit.ui.TimeText.age(java.time.Duration.ofMinutes(240)));
        assertEquals("2d", net.vanfleteren.jazygit.ui.TimeText.age(java.time.Duration.ofDays(2)));
    }

    @Test
    void aRenamedBranchStaysHighlightedUnderItsNewName() throws Exception {
        Model after = with(before, List.of(MAIN, OLD, new Branch(new Branch.Name("topic"), false, new Sha("2"))));

        assertEquals("topic", highlightedAfterMovingTo(1, after));
    }

    @Test
    void shiftRAsksForANewNameForTheHighlightedBranch() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add, tab -> { });
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.ID))) {
            testRunner.runner().focusManager().setFocus(BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.ID));
            testRunner.pilot().press(KeyCode.DOWN);
            testRunner.pilot().press('R');
            testRunner.pilot().pause();
            testRunner.pilot().quit();
        }

        assertEquals(java.util.List.of(new net.vanfleteren.jazygit.feature.branch.RenameBranchMsg.Requested(
                new Branch.Name("feature"))), dispatched);
    }

    @Test
    void nAsksForANewBranchStartingAtTheHighlightedBranch() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add, tab -> { });
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.ID))) {
            testRunner.runner().focusManager().setFocus(BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.ID));
            testRunner.pilot().press(KeyCode.DOWN);
            testRunner.pilot().press('n');
            testRunner.pilot().pause();
            testRunner.pilot().quit();
        }

        assertEquals(java.util.List.of(new NewBranchMsg.Requested(new Branch.Name("feature"))), dispatched);
    }

    @Test
    void shiftPPushesTheHighlightedBranch() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add, tab -> { });
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.ID))) {
            testRunner.runner().focusManager().setFocus(BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.ID));
            testRunner.pilot().press(KeyCode.DOWN);
            testRunner.pilot().press('P');
            testRunner.pilot().pause();
            testRunner.pilot().quit();
        }

        assertEquals(java.util.List.of(new net.vanfleteren.jazygit.feature.branch.PushMsg.Requested(new Branch.Name("feature"))), dispatched);
    }

    @Test
    void pPullsTheHighlightedBranch() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add, tab -> { });
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.ID))) {
            testRunner.runner().focusManager().setFocus(BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.ID));
            testRunner.pilot().press(KeyCode.DOWN);
            testRunner.pilot().press('p');
            testRunner.pilot().pause();
            testRunner.pilot().quit();
        }

        assertEquals(java.util.List.of(new net.vanfleteren.jazygit.feature.branch.PullMsg.Requested(new Branch.Name("feature"))), dispatched);
    }

    @Test
    void questionMarkAsksForTheKeybindingsOfThePanel() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add, tab -> { });
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.ID))) {
            testRunner.runner().focusManager().setFocus(BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.ID));
            testRunner.pilot().press('?');
            testRunner.pilot().pause();
            testRunner.pilot().quit();
        }

        assertEquals(1, dispatched.size());
        assertTrue(dispatched.getFirst() instanceof net.vanfleteren.jazygit.feature.help.HelpMsg.Requested(
                String scopeId, var actions) && scopeId.equals(net.vanfleteren.jazygit.keys.Scopes.BRANCHES.id())
                && actions.head().isDefined(), dispatched.toString());
    }

    @Test
    void theRemoteTabListsTheRemoteBranchesUnderItsOwnTitle() throws Exception {
        Model remote = withRemotes(before);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(remote, BranchesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> panel.render(remote, BranchesPanel.ID));
            testRunner.pilot().quit();

            assertTrue(screen.contains("Branches │ Remotes"), screen);
            assertTrue(screen.contains("origin/feature"), screen);
            assertTrue(screen.contains("upstream/main"), screen);
            assertFalse(screen.contains(" old"), screen);
        }
        assertEquals(Option.of(new SelectionMsg.BranchSelected(new BranchRef.Remote(new RemoteBranch("origin", new Branch.Name("feature"))))),
                panel.selectionChange(remote));
    }

    @Test
    void theRemoteTabSaysWhenThereAreNoRemoteBranches() throws Exception {
        Model none = before.withBranchesTab(BranchesTab.REMOTE).withRemoteBranches(new Loadable.Loaded<>(List.empty()));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(none, BranchesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> panel.render(none, BranchesPanel.ID));
            testRunner.pilot().quit();

            assertTrue(screen.contains("No remote branches"), screen);
        }
        assertEquals(Option.none(), panel.selectionChange(none));
    }

    @Test
    void onTheRemoteTabSpaceChecksOutTheHighlightedRemoteBranchAndTheBracketsSwitchBack() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add, tab -> { });
        Model remote = withRemotes(before);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(remote, BranchesPanel.ID))) {
            testRunner.runner().focusManager().setFocus(BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(remote, BranchesPanel.ID));
            testRunner.pilot().press(KeyCode.DOWN);
            testRunner.pilot().press(' ');
            testRunner.pilot().press('n');
            testRunner.pilot().press('[');
            testRunner.pilot().press('P');
            testRunner.pilot().pause();
            testRunner.pilot().quit();
        }

        RemoteBranch upstreamMain = new RemoteBranch("upstream", new Branch.Name("main"));
        // P pushes a local branch; it does nothing here.
        assertEquals(java.util.List.of(new CheckoutMsg.RemoteRequested(upstreamMain),
                new NewBranchMsg.RequestedAtRemote(upstreamMain), new BranchesTabMsg.Switched()), dispatched);
    }

    @Test
    void onTheLocalTabTheBracketsSwitchToTheRemoteBranches() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add, tab -> { });
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.ID))) {
            testRunner.runner().focusManager().setFocus(BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.ID));
            testRunner.pilot().press(']');
            testRunner.pilot().pause();
            testRunner.pilot().quit();
        }

        assertEquals(java.util.List.of(new BranchesTabMsg.Switched()), dispatched);
    }

    @Test
    void clickingTheNameOfAListInTheTitleReportsThatList() throws Exception {
        java.util.List<BranchesTab> clicked = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(msg -> { }, clicked::add);
        int local = Messages.get("panel.branches.title").length();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, null))) {
            RenderedText.of(testRunner, () -> panel.render(before, null));
            // The title starts after the corner of the border.
            testRunner.pilot().click(local + 4, 0);
            testRunner.pilot().click(1, 0);
            testRunner.pilot().pause();
            testRunner.pilot().quit();
        }

        assertEquals(java.util.List.of(BranchesTab.REMOTE, BranchesTab.LOCAL), clicked);
    }

    @Test
    void onlyTheNamesInTheTitleAreTabs() {
        int local = Messages.get("panel.branches.title").length();
        int remote = Messages.get("panel.remoteBranches.title").length();

        assertEquals(Option.of(BranchesTab.LOCAL), BranchesPanel.tabAt(0));
        assertEquals(Option.of(BranchesTab.LOCAL), BranchesPanel.tabAt(local - 1));
        assertEquals(Option.none(), BranchesPanel.tabAt(local + 1));
        assertEquals(Option.of(BranchesTab.REMOTE), BranchesPanel.tabAt(local + 3));
        assertEquals(Option.of(BranchesTab.REMOTE), BranchesPanel.tabAt(local + 3 + remote - 1));
        assertEquals(Option.none(), BranchesPanel.tabAt(local + 3 + remote));
        assertEquals(Option.none(), BranchesPanel.tabAt(-1));
    }

    @Test
    void theTitleShowsWhichTabIsShownInBoldAndUnderlined() {
        assertEquals(List.of(true, false), List.ofAll(BranchesPanel.title(BranchesTab.REMOTE).spans())
                .filter(span -> !span.content().contains("│"))
                .map(span -> span.style().addModifiers().containsAll(java.util.Set.of(
                        dev.tamboui.style.Modifier.BOLD, dev.tamboui.style.Modifier.UNDERLINED)))
                .reverse());
    }

    private static Model withRemotes(Model model) {
        return model.withBranchesTab(BranchesTab.REMOTE).withRemoteBranches(new Loadable.Loaded<>(List.of(
                new RemoteRef(new RemoteBranch("origin", new Branch.Name("feature")), new Sha("2"), java.time.Instant.EPOCH),
                new RemoteRef(new RemoteBranch("upstream", new Branch.Name("main")), new Sha("1"), java.time.Instant.EPOCH))));
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
            return panel.selectedBranch().get().value();
        }
    }

    private static Model with(Model model, List<Branch> branches) {
        return Update.update(model, new LoadMsg.BranchesLoaded(branches)).model();
    }
}
