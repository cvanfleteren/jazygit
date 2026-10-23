package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.MouseButton;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutMsg;
import net.vanfleteren.jazygit.feature.branch.commits.BranchCommitsMsg;
import net.vanfleteren.jazygit.feature.branch.delete.DeleteBranchCmd;
import net.vanfleteren.jazygit.feature.branch.newbranch.NewBranchMsg;
import net.vanfleteren.jazygit.feature.branch.pull.PullCmd;
import net.vanfleteren.jazygit.feature.branch.push.PushCmd;
import net.vanfleteren.jazygit.feature.branch.tabs.BranchesTabMsg;
import net.vanfleteren.jazygit.feature.branch.upstream.UpstreamMsg;
import net.vanfleteren.jazygit.feature.worktree.add.AddWorktreeMsg;
import net.vanfleteren.jazygit.feature.worktree.remove.RemoveWorktreeMsg;
import net.vanfleteren.jazygit.feature.worktree.switchto.SwitchWorktreeMsg;
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
                                new Head.OnBranch(new Branch.Name("main"), Option.of(new Sha("1"))),
                                true,
                                true,
                                Option.none(),
                                false),
                        new Worktree(
                                java.nio.file.Path.of("/repos/app-feature"),
                                new Head.OnBranch(new Branch.Name("feature"), Option.of(new Sha("2"))),
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

        assertEquals(selecting("old"), highlightedAfterMovingTo(2, after));
    }

    @Test
    void aReorderingWithoutCheckoutKeepsTheSameBranchHighlighted() throws Exception {
        Model after = with(before, List.of(MAIN, OLD, FEATURE));

        assertEquals(selecting("feature"), highlightedAfterMovingTo(1, after));
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
    void ageIsShownInTheLargestFittingUnit() {
        assertEquals("now", net.vanfleteren.jazygit.ui.TimeText.age(java.time.Duration.ofSeconds(45)));
        assertEquals("1m", net.vanfleteren.jazygit.ui.TimeText.age(java.time.Duration.ofSeconds(60)));
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

        assertEquals(selecting("topic"), highlightedAfterMovingTo(1, after));
    }

    @Test
    void aDeletedBranchLeavesTheHighlightOnTheBranchThatTookItsPlace() throws Exception {
        Model after = with(before, List.of(MAIN, OLD));

        assertEquals(selecting("old"), highlightedAfterMovingTo(1, after));
    }

    @Test
    void aBranchListedFirstNowStaysHighlighted() throws Exception {
        Model after = with(before, List.of(OLD, MAIN, FEATURE));

        assertEquals(selecting("old"), highlightedAfterMovingTo(2, after));
    }

    @Test
    void whenTheLastBranchIsDeletedTheNewLastOneIsHighlighted() throws Exception {
        Model after = with(before, List.of(MAIN, FEATURE));

        assertEquals(selecting("feature"), highlightedAfterMovingTo(2, after));
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
                new Head.OnBranch(new Branch.Name("other"), Option.of(new Sha("3"))),
                false,
                false,
                Option.none(),
                false);
        Model three = two.mapRepo(repo -> repo.withWorktrees(
                new Loadable.Loaded<>(repo.worktrees().toOption().get().append(added))));

        panel.render(two, BranchesPanel.ID);
        panel.render(three, BranchesPanel.ID);
        assertEquals(selecting("main"), panel.selectionChange(three));

        panel.render(three.openPopup(new AboutWorktree("/repos/app-other")), BranchesPanel.ID);
        assertEquals(selecting("other"), panel.selectionChange(three));
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
            assertTrue(screen.contains("app ● main (main)"), screen);
            assertTrue(screen.contains("app-feature feature"), screen);
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
                new Loadable.Loaded<>(new Divergence(
                        new Divergence.Side(List.of(newer, older), 2), new Divergence.Side(List.of(behind), 1))),
                Option.none()))));
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        java.util.List<String> opened = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(
                dispatched::add,
                tab -> {},
                () -> opened.add("commit"),
                java.time.Clock.systemUTC(),
                Theme.Highlights.DEFAULT);
        java.util.List<Option<Msg>> highlighted = new java.util.ArrayList<>();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(diverged, BranchesPanel.ID))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            String screen = RenderedText.ofLarge(testRunner, () -> panel.render(diverged, BranchesPanel.ID));
            highlighted.add(panel.selectionChange(diverged));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).pause();
            highlighted.add(panel.selectionChange(diverged));
            SettledPilot.of(testRunner).press(KeyCode.UP);
            SettledPilot.of(testRunner).pause();
            highlighted.add(panel.selectionChange(diverged));
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
        assertEquals(
                java.util.List.of(
                        Option.of(new SelectionMsg.DivergenceCommitSelected(new Sha("b1"))),
                        Option.of(new SelectionMsg.DivergenceCommitSelected(new Sha("a2"))),
                        Option.of(new SelectionMsg.DivergenceCommitSelected(new Sha("b1")))),
                highlighted);
        assertEquals(java.util.List.of("commit"), opened);
        assertEquals(java.util.List.of(new UpstreamMsg.DivergenceClosed(), new BranchesTabMsg.Switched(1)), dispatched);
    }

    @Test
    void enterOnABranchOpensItsCommitsOnTheLocalAndTheRemoteTab() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel sending = new BranchesPanel(dispatched::add, tab -> {});
        Model remotes = withRemotes(before);
        for (Model shown : List.of(before, remotes)) {
            try (ToolkitTestRunner testRunner =
                    ToolkitTestRunner.runTest(() -> sending.render(shown, BranchesPanel.ID))) {
                SettledPilot.focus(testRunner, BranchesPanel.ID);
                RenderedText.of(testRunner, () -> sending.render(shown, BranchesPanel.ID));
                SettledPilot.of(testRunner).press(KeyCode.ENTER);
                SettledPilot.of(testRunner).pause();
                SettledPilot.of(testRunner).quit();
            }
        }

        assertEquals(
                java.util.List.of(
                        new BranchCommitsMsg.Opened(new BranchRef.Local(new Branch.Name("main"))),
                        new BranchCommitsMsg.Opened(
                                new BranchRef.Remote(new RemoteBranch("origin", new Branch.Name("feature"))))),
                dispatched);
    }

    @Test
    void theCommitsOfABranchAreListedInPlaceOfTheBranchesUnderItsNameAndHowManyAreAhead() throws Exception {
        Commit newer = new Commit(new Sha("a2"), "Ada Lovelace", "a@x", java.time.Instant.EPOCH, "Ours", "");
        Commit older = new Commit(new Sha("a1"), "Ada Lovelace", "a@x", java.time.Instant.EPOCH, "Shared", "");
        BranchRef feature = new BranchRef.Local(new Branch.Name("feature"));
        BranchRef base = new BranchRef.Local(new Branch.Name("main"));
        Model open = before.mapRepoView(repoView -> repoView.withBranchCommits(Option.of(BranchCommitsView.of(feature)))
                .withBranchLog(Option.of(new Preview<>(feature, new Loadable.Loaded<>(List.of(newer, older)))))
                .withBranchAhead(Option.of(new Preview<>(
                        feature,
                        new Loadable.Loaded<>(
                                Option.of(new Ahead(new Sha("a2"), base, List.of(newer), false, Option.of(older))))))));
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        java.util.List<String> opened = new java.util.ArrayList<>();
        BranchesPanel sending = new BranchesPanel(
                dispatched::add,
                tab -> {},
                () -> opened.add("commit"),
                java.time.Clock.systemUTC(),
                Theme.Highlights.DEFAULT);
        java.util.List<Option<Msg>> highlighted = new java.util.ArrayList<>();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> sending.render(open, BranchesPanel.ID))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            String screen = RenderedText.ofLarge(testRunner, () -> sending.render(open, BranchesPanel.ID));
            highlighted.add(sending.selectionChange(open));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).pause();
            highlighted.add(sending.selectionChange(open));
            SettledPilot.of(testRunner).press(KeyCode.ENTER);
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).press('b');
            SettledPilot.of(testRunner).press(KeyCode.ESCAPE);
            SettledPilot.of(testRunner).press(']');
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();

            assertTrue(screen.contains("feature · 1 ahead of main"), screen);
            assertTrue(screen.contains("a2 AL ○ Ours"), screen);
            assertTrue(screen.contains("a1 AL ○ Shared"), screen);
            assertFalse(screen.contains("Remote"), screen);
        }
        assertEquals(
                java.util.List.of(
                        Option.of(new SelectionMsg.BranchCommitSelected(new Sha("a2"))),
                        Option.of(new SelectionMsg.BranchCommitSelected(new Sha("a1")))),
                highlighted);
        // Enter moves the focus to the highlighted commit, and sends nothing.
        assertEquals(java.util.List.of("commit"), opened);
        assertEquals(
                java.util.List.of(
                        new AheadMsg.Toggled(), new BranchCommitsMsg.Closed(), new BranchesTabMsg.Switched(1)),
                dispatched);
        assertFalse(BranchesPanel.showsSpinner(open.withBusyBranch(feature, PushCmd.PUSH)));
    }

    /**
     * {@code before} listing the commits of {@code branch}, with {@code log} read as the log of {@code logOf}.
     */
    private Model listing(BranchRef branch, BranchRef logOf, List<Commit> log) {
        return before.mapRepoView(repoView -> repoView.withBranchCommits(Option.of(BranchCommitsView.of(branch)))
                .withBranchLog(Option.of(new Preview<>(logOf, new Loadable.Loaded<>(log)))));
    }

    private static Commit commit(String sha, String message) {
        return new Commit(new Sha(sha), "Ada Lovelace", "a@x", java.time.Instant.EPOCH, message, "");
    }

    @Test
    void theCommitsOfABranchOpenedAgainStartAtItsNewestEvenWhenTheOneBeforeHadTheHighlightedCommitToo()
            throws Exception {
        Commit shared = commit("a1", "Shared");
        BranchRef feature = new BranchRef.Local(new Branch.Name("feature"));
        BranchRef old = new BranchRef.Local(new Branch.Name("old"));
        java.util.concurrent.atomic.AtomicReference<Model> shown = new java.util.concurrent.atomic.AtomicReference<>(
                listing(feature, feature, List.of(commit("a2", "Ours"), shared)));
        Option<Msg> highlighted;
        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(shown.get(), BranchesPanel.ID))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(shown.get(), BranchesPanel.ID));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).pause();
            assertEquals(
                    Option.of(new SelectionMsg.BranchCommitSelected(shared.sha())), panel.selectionChange(shown.get()));
            shown.set(before);
            RenderedText.of(testRunner, () -> panel.render(shown.get(), BranchesPanel.ID));
            shown.set(listing(old, old, List.of(commit("b1", "Theirs"), shared)));
            RenderedText.of(testRunner, () -> panel.render(shown.get(), BranchesPanel.ID));
            highlighted = panel.selectionChange(shown.get());
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(Option.of(new SelectionMsg.BranchCommitSelected(new Sha("b1"))), highlighted);
    }

    @Test
    void theCommitsOfABranchWhoseLogIsStillReadShowThatAndThenStartAtItsNewest() throws Exception {
        BranchRef feature = new BranchRef.Local(new Branch.Name("feature"));
        BranchRef main = new BranchRef.Local(new Branch.Name("main"));
        java.util.concurrent.atomic.AtomicReference<Model> shown = new java.util.concurrent.atomic.AtomicReference<>(
                listing(feature, main, List.of(commit("c1", "On main"))));
        String loading;
        Option<Msg> whileLoading;
        Option<Msg> highlighted;
        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(shown.get(), BranchesPanel.ID))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            loading = RenderedText.of(testRunner, () -> panel.render(shown.get(), BranchesPanel.ID));
            whileLoading = panel.selectionChange(shown.get());
            shown.set(listing(feature, feature, List.of(commit("a2", "Ours"), commit("a1", "Older"))));
            RenderedText.of(testRunner, () -> panel.render(shown.get(), BranchesPanel.ID));
            highlighted = panel.selectionChange(shown.get());
            SettledPilot.of(testRunner).quit();
        }

        assertFalse(loading.contains("On main"), loading);
        assertTrue(loading.contains(Placeholders.loading()), loading);
        assertEquals(Option.none(), whileLoading);
        assertEquals(Option.of(new SelectionMsg.BranchCommitSelected(new Sha("a2"))), highlighted);
    }

    @Test
    void withOnlyTheCommitsAheadTheCommitsOfABranchEndAtTheForkPointWhichCanBeHighlighted() throws Exception {
        Commit ours = commit("a3", "Ours");
        Commit fork = commit("a2", "Fork");
        BranchRef feature = new BranchRef.Local(new Branch.Name("feature"));
        BranchRef base = new BranchRef.Local(new Branch.Name("main"));
        Model open = listing(feature, feature, List.of(ours, fork, commit("a1", "Older")))
                .mapRepoView(repoView -> repoView.withOnlyAhead(true)
                        .withBranchAhead(Option.of(new Preview<>(
                                feature,
                                new Loadable.Loaded<>(Option.of(
                                        new Ahead(ours.sha(), base, List.of(ours), false, Option.of(fork))))))));
        String screen;
        Option<Msg> highlighted;
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(open, BranchesPanel.ID))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            screen = RenderedText.of(testRunner, () -> panel.render(open, BranchesPanel.ID));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).pause();
            highlighted = panel.selectionChange(open);
            SettledPilot.of(testRunner).quit();
        }

        assertTrue(screen.contains("a3 AL ○ Ours"), screen);
        assertTrue(screen.contains("⑂ main Fork"), screen);
        assertFalse(screen.contains("Older"), screen);
        assertEquals(Option.of(new SelectionMsg.BranchCommitSelected(fork.sha())), highlighted);
    }

    /**
     * What the panel sends when the local branch {@code name} gets highlighted.
     */
    private static Option<Msg> selecting(String name) {
        return Option.of(new SelectionMsg.BranchSelected(new BranchRef.Local(new Branch.Name(name))));
    }

    private Option<Msg> highlightedAfterMovingTo(int index, Model after) throws Exception {
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.ID))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.ID));
            for (int i = 0; i < index; i++) {
                SettledPilot.of(testRunner).press(KeyCode.DOWN);
            }
            SettledPilot.of(testRunner).pause();
            assertEquals(
                    selecting(List.of(MAIN, FEATURE, OLD).get(index).name().value()), panel.selectionChange(before));

            RenderedText.of(testRunner, () -> panel.render(after, BranchesPanel.ID));
            SettledPilot.of(testRunner).quit();
            return panel.selectionChange(after);
        }
    }
}
