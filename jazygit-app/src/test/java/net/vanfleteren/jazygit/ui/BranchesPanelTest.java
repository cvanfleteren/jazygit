package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.MouseButton;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutMsg;
import net.vanfleteren.jazygit.feature.branch.delete.DeleteBranchCmd;
import net.vanfleteren.jazygit.feature.branch.newbranch.NewBranchMsg;
import net.vanfleteren.jazygit.feature.branch.pull.PullCmd;
import net.vanfleteren.jazygit.feature.branch.push.PushCmd;
import net.vanfleteren.jazygit.feature.branch.tabs.BranchesTabMsg;
import net.vanfleteren.jazygit.feature.branch.upstream.UpstreamMsg;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeMsg;
import net.vanfleteren.jazygit.feature.worktree.remove.RemoveWorktreeMsg;
import net.vanfleteren.jazygit.feature.worktree.switchto.SwitchWorktreeMsg;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.TestModels;
import org.junit.jupiter.api.Test;

/**
 * The highlighted row of the branches pane follows its branch when the list is reordered.
 */
class BranchesPanelTest {

    private static final Branch MAIN = new Branch(new Branch.Name("main"), true, new Sha("1"));
    private static final Branch FEATURE = new Branch(new Branch.Name("feature"), false, new Sha("2"));
    private static final Branch OLD = new Branch(new Branch.Name("old"), false, new Sha("3"));

    private final BranchesPanel panel = new BranchesPanel(msg -> {}, tab -> {});
    private final Model before = with(TestModels.loaded(new SampleData()), List.of(MAIN, FEATURE, OLD));

    private static Model withWorktrees(Model model) {
        return model.mapRepoView(repoView -> repoView.withBranchesTab(BranchesTab.WORKTREES))
                .mapRepo(repo -> repo.withWorktrees(new Loadable.Loaded<>(List.of(
                        new Worktree(
                                java.nio.file.Path.of("/repos/app"),
                                Option.of(new Sha("1")),
                                Option.of(new Branch.Name("main")),
                                true,
                                true,
                                Option.none(),
                                false),
                        new Worktree(
                                java.nio.file.Path.of("/repos/app-feature"),
                                Option.of(new Sha("2")),
                                Option.of(new Branch.Name("feature")),
                                false,
                                false,
                                Option.none(),
                                false)))));
    }

    private static Model withRemotes(Model model) {
        return model.mapRepoView(repoView -> repoView.withBranchesTab(BranchesTab.REMOTE))
                .mapRepo(repo -> repo.withRemoteBranches(new Loadable.Loaded<>(List.of(
                        new RemoteRef(
                                new RemoteBranch("origin", new Branch.Name("feature")),
                                new Sha("2"),
                                java.time.Instant.EPOCH),
                        new RemoteRef(
                                new RemoteBranch("upstream", new Branch.Name("main")),
                                new Sha("1"),
                                java.time.Instant.EPOCH)))));
    }

    private static Model with(Model model, List<Branch> branches) {
        return TestModels.localBranchesRead(model, branches).model();
    }

    @Test
    void afterACheckoutTheNewCurrentBranchIsHighlighted() throws Exception {
        Model after = with(
                before,
                List.of(
                        new Branch(new Branch.Name("old"), true, new Sha("3")),
                        new Branch(new Branch.Name("main"), false, new Sha("1")),
                        FEATURE));

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
        assertEquals(
                List.of(
                        LoadableList.Seg.styled(" #42", Theme.Classes.PULL_REQUEST),
                        LoadableList.Seg.styled(" ✓", Theme.Classes.CHECKS_PASSED),
                        LoadableList.Seg.styled(" 👍", Theme.Classes.REVIEW_APPROVED)),
                BranchesPanel.pullRequest(Option.of(new PullRequest(42, "b", false, Checks.PASSING, Review.APPROVED))));
        assertEquals(
                List.of(
                        LoadableList.Seg.styled(" #40", Theme.Classes.PULL_REQUEST),
                        LoadableList.Seg.styled(" ✗", Theme.Classes.CHECKS_FAILED),
                        LoadableList.Seg.styled(" ?", Theme.Classes.DIM)),
                BranchesPanel.pullRequest(Option.of(new PullRequest(40, "b", false, Checks.FAILING, Review.REQUIRED))));
        assertEquals(
                List.of(
                        LoadableList.Seg.styled(" #38", Theme.Classes.PULL_REQUEST),
                        LoadableList.Seg.styled(" ●", Theme.Classes.CHECKS_RUNNING),
                        LoadableList.Seg.styled(" ✎", Theme.Classes.REVIEW_CHANGES)),
                BranchesPanel.pullRequest(
                        Option.of(new PullRequest(38, "b", false, Checks.PENDING, Review.CHANGES_REQUESTED))));
        // A draft without checks or reviews shows only its number, dimmed.
        assertEquals(
                List.of(LoadableList.Seg.styled(" #37", Theme.Classes.DIM)),
                BranchesPanel.pullRequest(Option.of(new PullRequest(37, "b", true, Checks.NONE, Review.NONE))));
    }

    @Test
    void aBranchShowsItsPullRequestElseTheChecksOnItsTip() {
        PullRequest pr = new PullRequest(42, "b", false, Checks.NONE, Review.NONE);

        assertEquals(
                List.of(LoadableList.Seg.styled(" #42", Theme.Classes.PULL_REQUEST)),
                BranchesPanel.gitHub(Option.of(pr), Option.of(Checks.FAILING)));
        assertEquals(
                List.of(LoadableList.Seg.styled(" ✗", Theme.Classes.CHECKS_FAILED)),
                BranchesPanel.gitHub(Option.none(), Option.of(Checks.FAILING)));
        assertEquals(List.of(), BranchesPanel.gitHub(Option.none(), Option.of(Checks.NONE)));
        assertEquals(List.of(), BranchesPanel.gitHub(Option.none(), Option.none()));
    }

    @Test
    void aBranchWhoseUpstreamIsGoneSaysSo() {
        assertEquals(
                List.of(LoadableList.Seg.styled(" gone", Theme.Classes.GONE)),
                BranchesPanel.sync(new Branch(
                        new Branch.Name("b"),
                        false,
                        new Sha("1"),
                        java.time.Instant.EPOCH,
                        false,
                        false,
                        new Tracking.Gone(new RemoteBranch("origin", new Branch.Name("b"))))));
        // A branch that was never pushed shows nothing.
        assertEquals(List.of(), BranchesPanel.sync(new Branch(new Branch.Name("b"), false, new Sha("1"))));
    }

    @Test
    void aBranchCheckedOutInAnotherWorktreeSaysSo() {
        assertEquals(
                List.of(LoadableList.Seg.styled(" (worktree)", Theme.Classes.DIM)),
                BranchesPanel.worktree(new Branch(
                        new Branch.Name("b"),
                        false,
                        new Sha("1"),
                        java.time.Instant.EPOCH,
                        false,
                        false,
                        new Tracking.None(),
                        Option.of(java.nio.file.Path.of("/repos/linked")))));
        assertEquals(List.of(), BranchesPanel.worktree(new Branch(new Branch.Name("b"), false, new Sha("1"))));
    }

    @Test
    void syncShowsAnAheadArrowForUnpushedAndABehindOneForIncomingCommits() {
        java.time.Instant t = java.time.Instant.EPOCH;
        assertEquals(
                List.of(),
                BranchesPanel.sync(new Branch(
                        new Branch.Name("b"),
                        false,
                        new Sha("1"),
                        t,
                        true,
                        false,
                        Option.of(new Upstream(new RemoteBranch("origin", new Branch.Name("b")), 0, 0)))));
        assertEquals(
                List.of(LoadableList.Seg.styled(" ↑2", Theme.Classes.AHEAD)),
                BranchesPanel.sync(new Branch(
                        new Branch.Name("b"),
                        false,
                        new Sha("1"),
                        t,
                        true,
                        false,
                        Option.of(new Upstream(new RemoteBranch("origin", new Branch.Name("b")), 2, 0)))));
        assertEquals(
                List.of(LoadableList.Seg.styled(" ↓1", Theme.Classes.BEHIND)),
                BranchesPanel.sync(new Branch(
                        new Branch.Name("b"),
                        false,
                        new Sha("1"),
                        t,
                        true,
                        false,
                        Option.of(new Upstream(new RemoteBranch("origin", new Branch.Name("b")), 0, 1)))));
        assertEquals(
                List.of(
                        LoadableList.Seg.styled(" ↑2", Theme.Classes.AHEAD),
                        LoadableList.Seg.styled(" ↓1", Theme.Classes.BEHIND)),
                BranchesPanel.sync(new Branch(
                        new Branch.Name("b"),
                        false,
                        new Sha("1"),
                        t,
                        true,
                        false,
                        Option.of(new Upstream(new RemoteBranch("origin", new Branch.Name("b")), 2, 1)))));
    }

    @Test
    void spinnerFramesBounceAsTimePasses() {
        java.util.Set<String> frames = java.util.stream.IntStream.range(0, 20)
                .mapToObj(
                        i -> net.vanfleteren.jazygit.ui.TimeText.spinnerFrame(java.time.Instant.ofEpochMilli(i * 120L)))
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(
                java.util.Set.copyOf(
                        java.util.Arrays.asList(dev.tamboui.widgets.spinner.SpinnerStyle.BOUNCING_BAR.frames())),
                frames);
    }

    @Test
    void aBranchBeingPushedOrPulledShowsWhatIsDoneLeftOfTheSpinner() throws Exception {
        Model busy = before.withBusyBranch(new BranchRef.Local(new Branch.Name("feature")), PushCmd.PUSH)
                .withBusyBranch(new BranchRef.Local(new Branch.Name("main")), PullCmd.PULL);
        String frame = net.vanfleteren.jazygit.ui.TimeText.spinnerFrame(java.time.Instant.EPOCH);
        BranchesPanel panel = new BranchesPanel(
                msg -> {}, tab -> {}, java.time.Clock.fixed(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(busy, BranchesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> panel.render(busy, BranchesPanel.ID));
            assertTrue(screen.contains("feature Pushing " + frame), screen);
            assertTrue(screen.contains("main Pulling " + frame), screen);
        }
    }

    @Test
    void aBranchBeingDeletedShowsOnlyTheSpinner() throws Exception {
        Model deleting =
                before.withBusyBranch(new BranchRef.Local(new Branch.Name("feature")), DeleteBranchCmd.DELETE_BRANCH);
        String frame = net.vanfleteren.jazygit.ui.TimeText.spinnerFrame(java.time.Instant.EPOCH);
        BranchesPanel panel = new BranchesPanel(
                msg -> {}, tab -> {}, java.time.Clock.fixed(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(deleting, BranchesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> panel.render(deleting, BranchesPanel.ID));
            assertTrue(screen.contains("feature " + frame), screen);
            assertFalse(screen.contains("main " + frame), screen);
        }
    }

    @Test
    void aFastForwardIsShownAsAPull() {
        assertEquals(BranchesPanel.busyLabel(PullCmd.PULL), BranchesPanel.busyLabel(PullCmd.FAST_FORWARD));
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
        BranchesPanel panel = new BranchesPanel(dispatched::add, tab -> {});
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.ID))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.ID));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).press('R');
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(
                java.util.List.of(new net.vanfleteren.jazygit.feature.branch.rename.RenameBranchMsg.Requested(
                        new Branch.Name("feature"))),
                dispatched);
    }

    @Test
    void nAsksForANewBranchStartingAtTheHighlightedBranch() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add, tab -> {});
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.ID))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.ID));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).press('n');
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(java.util.List.of(new NewBranchMsg.Requested(new Branch.Name("feature"))), dispatched);
    }

    @Test
    void shiftPPushesTheHighlightedBranch() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add, tab -> {});
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.ID))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.ID));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).press('P');
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(
                java.util.List.of(
                        new net.vanfleteren.jazygit.feature.branch.push.PushMsg.Requested(new Branch.Name("feature"))),
                dispatched);
    }

    @Test
    void pPullsTheHighlightedBranch() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add, tab -> {});
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.ID))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.ID));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).press('p');
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(
                java.util.List.of(
                        new net.vanfleteren.jazygit.feature.branch.pull.PullMsg.Requested(new Branch.Name("feature"))),
                dispatched);
    }

    @Test
    void questionMarkAsksForTheKeybindingsOfThePanel() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add, tab -> {});
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.ID))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.ID));
            SettledPilot.of(testRunner).press('?');
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(1, dispatched.size());
        assertTrue(
                dispatched.getFirst()
                                instanceof
                                net.vanfleteren.jazygit.feature.help.HelpMsg.Requested(String scopeId, var actions)
                        && scopeId.equals(net.vanfleteren.jazygit.keys.Scopes.BRANCHES.id())
                        && actions.head().isDefined(),
                dispatched.toString());
    }

    @Test
    void theRemoteTabListsTheRemoteBranchesUnderItsOwnTitle() throws Exception {
        Model remote = withRemotes(before);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(remote, BranchesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> panel.render(remote, BranchesPanel.ID));
            SettledPilot.of(testRunner).quit();

            assertTrue(screen.contains("Branches │ Remotes"), screen);
            assertTrue(screen.contains("origin/feature"), screen);
            assertTrue(screen.contains("upstream/main"), screen);
            assertFalse(screen.contains(" old"), screen);
        }
        assertEquals(
                Option.of(new SelectionMsg.BranchSelected(
                        new BranchRef.Remote(new RemoteBranch("origin", new Branch.Name("feature"))))),
                panel.selectionChange(remote));
    }

    @Test
    void clickingARemoteBranchHighlightsIt() throws Exception {
        Model remote = withRemotes(before);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(remote, BranchesPanel.ID))) {
            // The title, origin/feature, then upstream/main.
            SettledPilot.of(testRunner).mousePress(MouseButton.LEFT, 5, 2);
            SettledPilot.of(testRunner).quit();
        }
        assertEquals(
                Option.of(new SelectionMsg.BranchSelected(
                        new BranchRef.Remote(new RemoteBranch("upstream", new Branch.Name("main"))))),
                panel.selectionChange(remote));
    }

    /**
     * A popup about the worktree at {@code location}.
     */
    private record AboutWorktree(String location) implements WorktreePopup {}

    @Test
    void aWorktreeAddedOutsideTheAppLeavesTheHighlightButTheOneAPopupIsAboutGetsIt() {
        BranchesPanel panel = new BranchesPanel(msg -> {}, tab -> {});
        Model two = withWorktrees(before);
        Worktree added = new Worktree(
                java.nio.file.Path.of("/repos/app-other"),
                Option.of(new Sha("3")),
                Option.of(new Branch.Name("other")),
                false,
                false,
                Option.none(),
                false);
        Model three = two.mapRepo(repo -> repo.withWorktrees(
                new Loadable.Loaded<>(repo.worktrees().toOption().get().append(added))));

        panel.render(two, BranchesPanel.ID);
        panel.render(three, BranchesPanel.ID);
        assertEquals("/repos/app", panel.selectedWorktree().get().location());

        panel.render(three.openPopup(new AboutWorktree("/repos/app-other")), BranchesPanel.ID);
        assertEquals("/repos/app-other", panel.selectedWorktree().get().location());
    }

    @Test
    void theWorktreesTabListsTheWorktreesAndItsKeysActOnTheHighlightedOne() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add, tab -> {});
        Model worktrees = withWorktrees(before);
        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(worktrees, BranchesPanel.ID))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            String screen = RenderedText.of(testRunner, () -> panel.render(worktrees, BranchesPanel.ID));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).press(' ');
            SettledPilot.of(testRunner).press('d');
            SettledPilot.of(testRunner).press(']');
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();

            assertTrue(screen.contains("Branches │ Remotes │ Worktrees"), screen);
            // The sample status has changes.
            assertTrue(screen.contains("* app ● main (main)"), screen);
            assertTrue(screen.contains("  app-feature feature"), screen);
        }

        assertEquals(
                java.util.List.of(
                        new SwitchWorktreeMsg.Requested("/repos/app-feature"),
                        new RemoveWorktreeMsg.Requested("/repos/app-feature"),
                        new BranchesTabMsg.Switched(1)),
                dispatched);
        assertEquals(
                Option.of(new SelectionMsg.BranchSelected(new BranchRef.Local(new Branch.Name("feature")))),
                panel.selectionChange(worktrees));
    }

    @Test
    void aWorktreeSaysWhatItHasCheckedOutAndWhatKeepsItFromBeingRemoved() {
        Worktree detached = new Worktree(
                java.nio.file.Path.of("/repos/x"),
                Option.of(new Sha("1234567890")),
                Option.none(),
                false,
                false,
                Option.of(""),
                true);

        assertEquals(
                "  x (detached at 1234567) locked missing",
                BranchesPanel.worktreeRow(Repo.LOADING, detached).text());
        assertEquals(
                List.of(
                        Option.none(),
                        Option.of(Theme.Classes.DIM),
                        Option.of(Theme.Classes.DIM),
                        Option.of(Theme.Classes.GONE)),
                BranchesPanel.worktreeRow(Repo.LOADING, detached).segs().map(LoadableList.Seg::cssClass));
    }

    @Test
    void aWorktreeWithChangesIsMarkedAndItsBranchSaysHowFarItIsFromItsUpstream() {
        java.nio.file.Path path = java.nio.file.Path.of("/repos/x");
        Worktree worktree = new Worktree(
                path,
                Option.of(new Sha("1234567890")),
                Option.of(new Branch.Name("feature")),
                false,
                false,
                Option.none(),
                false);
        Repo repo = Repo.LOADING
                .withBranches(new Loadable.Loaded<>(List.of(new Branch(
                        new Branch.Name("feature"),
                        false,
                        new Sha("1234567890"),
                        java.time.Instant.EPOCH,
                        true,
                        false,
                        Option.of(new Upstream(new RemoteBranch("origin", new Branch.Name("feature")), 2, 1))))))
                .withWorktreeChanges(io.vavr.collection.HashMap.of(path.toString(), true));

        LoadableList.Row row = BranchesPanel.worktreeRow(repo, worktree);

        assertEquals("  x ● feature ↑2 ↓1", row.text());
        assertEquals(
                List.of(
                        Option.none(),
                        Option.of(Theme.Classes.UNSTAGED),
                        Option.none(),
                        Option.of(Theme.Classes.AHEAD),
                        Option.of(Theme.Classes.BEHIND)),
                row.segs().map(LoadableList.Seg::cssClass));
        assertEquals(
                "  x feature ↑2 ↓1",
                BranchesPanel.worktreeRow(
                                repo.withWorktreeChanges(io.vavr.collection.HashMap.of(path.toString(), false)),
                                worktree)
                        .text());
    }

    @Test
    void aWorktreeLockedForAReasonSaysWhy() {
        Worktree locked = new Worktree(
                java.nio.file.Path.of("/repos/x"),
                Option.of(new Sha("1234567890")),
                Option.of(new Branch.Name("feature")),
                false,
                false,
                Option.of("on usb"),
                false);

        assertEquals(
                "  x feature locked: on usb",
                BranchesPanel.worktreeRow(Repo.LOADING, locked).text());
    }

    @Test
    void onTheLocalTabWChecksTheHighlightedBranchOutInANewWorktree() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add, tab -> {});
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.ID))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.ID));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).press('w');
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(java.util.List.of(new AddWorktreeMsg.Requested(new Branch.Name("feature"))), dispatched);
    }

    @Test
    void theRemoteTabSaysWhenThereAreNoRemoteBranches() throws Exception {
        Model none = before.mapRepoView(repoView -> repoView.withBranchesTab(BranchesTab.REMOTE))
                .mapRepo(repo -> repo.withRemoteBranches(new Loadable.Loaded<>(List.empty())));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(none, BranchesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> panel.render(none, BranchesPanel.ID));
            SettledPilot.of(testRunner).quit();

            assertTrue(screen.contains("No remote branches"), screen);
        }
        assertEquals(Option.none(), panel.selectionChange(none));
    }

    @Test
    void onTheRemoteTabSpaceChecksOutTheHighlightedRemoteBranchAndTheBracketsSwitchBack() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add, tab -> {});
        Model remote = withRemotes(before);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(remote, BranchesPanel.ID))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(remote, BranchesPanel.ID));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).press(' ');
            SettledPilot.of(testRunner).press('n');
            SettledPilot.of(testRunner).press('[');
            SettledPilot.of(testRunner).press('P');
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }

        RemoteBranch upstreamMain = new RemoteBranch("upstream", new Branch.Name("main"));
        // P pushes a local branch; it does nothing here.
        assertEquals(
                java.util.List.of(
                        new CheckoutMsg.RemoteRequested(upstreamMain),
                        new NewBranchMsg.RequestedAtRemote(upstreamMain),
                        new BranchesTabMsg.Switched(-1)),
                dispatched);
    }

    @Test
    void onTheLocalTabTheBracketsSwitchToTheRemoteBranches() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add, tab -> {});
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.ID))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.ID));
            SettledPilot.of(testRunner).press(']');
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(java.util.List.of(new BranchesTabMsg.Switched(1)), dispatched);
    }

    @Test
    void clickingTheNameOfAListInTheTitleReportsThatList() throws Exception {
        java.util.List<BranchesTab> clicked = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(msg -> {}, clicked::add);
        int local = Messages.get("panel.branches.title").length();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, null))) {
            RenderedText.of(testRunner, () -> panel.render(before, null));
            // The title starts after the corner of the border.
            SettledPilot.of(testRunner).click(local + 4, 0);
            SettledPilot.of(testRunner).click(1, 0);
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
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
        assertEquals(Option.of(BranchesTab.WORKTREES), BranchesPanel.tabAt(local + 3 + remote + 3));
        assertEquals(
                Option.none(),
                BranchesPanel.tabAt(local
                        + 3
                        + remote
                        + 3
                        + Messages.get("panel.worktrees.title").length()));
        assertEquals(Option.none(), BranchesPanel.tabAt(-1));
    }

    @Test
    void theTitleShowsWhichTabIsShownInBoldAndUnderlined() {
        assertEquals(
                List.of(false, true, false),
                List.ofAll(BranchesPanel.title(BranchesTab.REMOTE).spans())
                        .filter(span -> !span.content().contains("│"))
                        .map(span -> span.style()
                                .addModifiers()
                                .containsAll(java.util.Set.of(
                                        dev.tamboui.style.Modifier.BOLD, dev.tamboui.style.Modifier.UNDERLINED))));
    }

    @Test
    void aDivergenceListsTheCommitsOfEitherBranchUnderItsNameAndTheHighlightSkipsTheNames() throws Exception {
        Commit behind = new Commit(new Sha("b1"), "Ada Lovelace", "a@x", java.time.Instant.EPOCH, "Theirs", "");
        Commit newer = new Commit(new Sha("a2"), "Ada Lovelace", "a@x", java.time.Instant.EPOCH, "Ours two", "");
        Commit older = new Commit(new Sha("a1"), "Ada Lovelace", "a@x", java.time.Instant.EPOCH, "Ours one", "");
        RemoteBranch upstream = new RemoteBranch("origin", new Branch.Name("feature"));
        Model diverged = before.mapRepoView(repoView -> repoView.withDivergence(Option.of(new DivergenceView(
                new Branch.Name("feature"),
                new BranchRef.Remote(upstream),
                DivergenceView.Against.UPSTREAM,
                new Loadable.Loaded<>(new Divergence(List.of(newer, older), 2, List.of(behind), 1)),
                Option.none()))));
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        java.util.List<String> opened = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(
                dispatched::add,
                tab -> {},
                () -> opened.add("commit"),
                java.time.Clock.systemUTC(),
                Theme.Highlights.DEFAULT);
        java.util.List<Integer> highlighted = new java.util.ArrayList<>();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(diverged, BranchesPanel.ID))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            String screen = RenderedText.ofLarge(testRunner, () -> panel.render(diverged, BranchesPanel.ID));
            highlighted.add(panel.selectedIndex(diverged));
            assertEquals(
                    Option.of(new SelectionMsg.DivergenceCommitSelected(new Sha("b1"))),
                    panel.selectionChange(diverged));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).pause();
            highlighted.add(panel.selectedIndex(diverged));
            SettledPilot.of(testRunner).press(KeyCode.UP);
            SettledPilot.of(testRunner).pause();
            highlighted.add(panel.selectedIndex(diverged));
            SettledPilot.of(testRunner).press(KeyCode.ENTER);
            SettledPilot.of(testRunner).press(KeyCode.ESCAPE);
            SettledPilot.of(testRunner).press(']');
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();

            assertTrue(screen.contains("Branches (feature ↔ origin/feature)"), screen);
            assertTrue(screen.contains("── origin/feature"), screen);
            assertTrue(screen.contains("↓ b1 AL ○ Theirs"), screen);
            assertTrue(screen.contains("── feature"), screen);
            assertTrue(screen.contains("↑ a2 AL ○ Ours two"), screen);
            assertFalse(screen.contains(" old"), screen);
        }
        // The rows are: origin/feature, Theirs, feature, Ours two, Ours one.
        assertEquals(java.util.List.of(1, 3, 1), highlighted);
        assertEquals(java.util.List.of("commit"), opened);
        assertEquals(java.util.List.of(new UpstreamMsg.DivergenceClosed(), new BranchesTabMsg.Switched(1)), dispatched);
    }

    @Test
    void aDivergenceSaysWhenABranchHasNoCommitsOfItsOwnOrMoreThanAreListed() {
        DivergenceView view = DivergenceView.loading(
                new Branch.Name("feature"), new BranchRef.Local(new Branch.Name("main")), DivergenceView.Against.BASE);
        Commit ours = new Commit(new Sha("a1"), "Ada", "a@x", java.time.Instant.EPOCH, "Ours", "");

        List<String> rows = BranchesPanel.entries(new Divergence(List.of(ours), 400, List.of(), 0))
                .map(entry -> BranchesPanel.divergenceRow(view, entry))
                .map(LoadableList.Row::text);

        assertEquals(List.of("── main", "  no commits", "── feature", "↑ a1 Ad ○ Ours", "  … 399 more"), rows);
    }

    private String highlightedAfterMovingTo(int index, Model after) throws Exception {
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.ID))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.ID));
            for (int i = 0; i < index; i++) {
                SettledPilot.of(testRunner).press(KeyCode.DOWN);
            }
            SettledPilot.of(testRunner).pause();
            assertEquals(index, panel.selectedIndex(before));

            RenderedText.of(testRunner, () -> panel.render(after, BranchesPanel.ID));
            SettledPilot.of(testRunner).quit();
            return panel.selectedBranch().get().value();
        }
    }
}
