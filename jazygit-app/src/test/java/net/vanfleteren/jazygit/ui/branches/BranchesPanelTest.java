package net.vanfleteren.jazygit.ui.branches;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.MouseButton;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.branches.branch.checkout.CheckoutMsg;
import net.vanfleteren.jazygit.branches.branch.commits.BranchCommitsMsg;
import net.vanfleteren.jazygit.branches.branch.delete.DeleteBranchCmd;
import net.vanfleteren.jazygit.branches.branch.newbranch.NewBranchMsg;
import net.vanfleteren.jazygit.branches.branch.pull.PullCmd;
import net.vanfleteren.jazygit.branches.branch.push.PushCmd;
import net.vanfleteren.jazygit.branches.branch.tabs.BranchesTabMsg;
import net.vanfleteren.jazygit.branches.branch.upstream.UpstreamMsg;
import net.vanfleteren.jazygit.branches.worktree.add.AddWorktreeMsg;
import net.vanfleteren.jazygit.branches.worktree.remove.RemoveWorktreeMsg;
import net.vanfleteren.jazygit.branches.worktree.switchto.SwitchWorktreeMsg;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.load.*;
import net.vanfleteren.jazygit.state.popup.*;
import net.vanfleteren.jazygit.state.view.*;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.i18n.Messages;
import net.vanfleteren.jazygit.ui.text.Placeholders;
import net.vanfleteren.jazygit.ui.text.ShownTime;
import net.vanfleteren.jazygit.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * The highlighted row of the branches pane follows its branch when the list is reordered.
 */
class BranchesPanelTest {

    private static final Branch MAIN = Fixtures.branch(new BranchName("main"), true, new Sha("1"));
    private static final Branch FEATURE = Fixtures.branch(new BranchName("feature"), false, new Sha("2"));
    private static final Branch OLD = Fixtures.branch(new BranchName("old"), false, new Sha("3"));

    private final BranchesPanel panel = new BranchesPanel(msg -> {}, tab -> {});
    private final Model before = with(TestModels.loaded(SampleData.git()), List.of(MAIN, FEATURE, OLD));

    private static Model withWorktrees(Model model) {
        return model.mapRepoView(repoView -> repoView.mapBranches(branches -> branches.withTab(BranchesTab.WORKTREES)))
                .mapRepo(repo -> repo.withWorktrees(new Loadable.Loaded<>(List.of(
                        new Worktree(
                                java.nio.file.Path.of("/repos/app"),
                                Option.of(new Head.OnBranch(new BranchName("main"), Option.of(new Sha("1")))),
                                true,
                                true,
                                Option.none(),
                                false),
                        new Worktree(
                                java.nio.file.Path.of("/repos/app-feature"),
                                Option.of(new Head.OnBranch(new BranchName("feature"), Option.of(new Sha("2")))),
                                false,
                                false,
                                Option.none(),
                                false)))));
    }

    private static Model withRemotes(Model model) {
        return model.mapRepoView(repoView -> repoView.mapBranches(branches -> branches.withTab(BranchesTab.REMOTE)))
                .mapRepo(repo -> repo.withRemoteBranches(new Loadable.Loaded<>(List.of(
                        new RemoteRef(
                                new RemoteBranch("origin", new BranchName("feature")),
                                new Sha("2"),
                                java.time.Instant.EPOCH),
                        new RemoteRef(
                                new RemoteBranch("upstream", new BranchName("main")),
                                new Sha("1"),
                                java.time.Instant.EPOCH)))));
    }

    private static Model with(Model model, List<Branch> branches) {
        return TestModels.localBranchesRead(model, branches).model();
    }

    @Test
    void theAgeOfABranchChangesTheMomentItsShownTimeSays() throws Exception {
        java.time.Instant committed = java.time.Instant.parse("2026-11-14T10:00:00Z");
        java.time.Instant now = committed.plusSeconds(3 * 60 + 20);
        Model model = with(
                        TestModels.loaded(SampleData.git()),
                        List.of(MAIN, Fixtures.branch(new BranchName("recent"), false, new Sha("4"), committed, false)))
                .withNow(now);
        java.time.Instant next =
                ShownTime.next(BranchesPanel.shownTimes(model), now).get();
        String shown;
        String justBefore;
        String changed;
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, BranchesPanel.PANE))) {
            shown = RenderedText.ofLarge(testRunner, () -> panel.render(model, BranchesPanel.PANE));
            justBefore = RenderedText.ofLarge(
                    testRunner, () -> panel.render(model.withNow(next.minusMillis(1)), BranchesPanel.PANE));
            changed = RenderedText.ofLarge(testRunner, () -> panel.render(model.withNow(next), BranchesPanel.PANE));
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(now.plusSeconds(40), next);
        assertEquals(shown, justBefore);
        assertTrue(shown.contains("3m"), shown);
        assertTrue(changed.contains("4m"), changed);
    }

    @Test
    void afterACheckoutTheNewCurrentBranchIsHighlighted() throws Exception {
        Model after = with(
                before,
                List.of(
                        Fixtures.branch(new BranchName("old"), true, new Sha("3")),
                        Fixtures.branch(new BranchName("main"), false, new Sha("1")),
                        FEATURE));

        assertEquals(selecting("old"), highlightedAfterMovingTo(2, after));
    }

    @Test
    void aDetachedHeadIsListedFirstAndHighlightedOnceDetachedWithoutALog() throws Exception {
        Sha at = new Sha("89eeda75aaaaaaaa");
        Model detached = detachedAt(at);

        assertEquals(
                Option.of(new SelectionMsg.NoLogSelected(new NoLog.Detached(at))),
                highlightedAfterMovingTo(2, detached));
        String screen;
        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(detached, BranchesPanel.PANE))) {
            screen = RenderedText.ofLarge(testRunner, () -> panel.render(detached, BranchesPanel.PANE));
            SettledPilot.of(testRunner).quit();
        }
        List<String> rows = List.of(screen.split("\n")).filter(line -> line.startsWith("│"));
        assertTrue(rows.head().contains("(HEAD detached at " + at.shortSha() + ")"), screen);
    }

    @Test
    void theKeysOfABranchDoNothingOnADetachedHead() throws Exception {
        Model detached = detachedAt(new Sha("89eeda75aaaaaaaa"));
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add, tab -> {});
        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(detached, BranchesPanel.PANE))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(detached, BranchesPanel.PANE));
            SettledPilot.of(testRunner).press('R');
            SettledPilot.of(testRunner).press(' ');
            SettledPilot.of(testRunner).press('d');
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(java.util.List.of(), dispatched);
    }

    @Test
    void noDetachedHeadIsListedWhileARebaseDetachesIt() throws Exception {
        Sha at = new Sha("89eeda75aaaaaaaa");
        Model rebasing = Update.update(
                        detachedAt(at),
                        new LoadMsg.StatusLoaded(new RepoStatus(
                                new Head.Detached(at),
                                List.empty(),
                                Option.of(new InProgress.Rebasing(
                                        Option.of(new BranchName("main")), new Sha("2"), 1, 2)))))
                .model();

        String screen;
        Option<Msg> selected;
        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(rebasing, BranchesPanel.PANE))) {
            screen = RenderedText.ofLarge(testRunner, () -> panel.render(rebasing, BranchesPanel.PANE));
            selected = panel.selectionChange(rebasing);
            SettledPilot.of(testRunner).quit();
        }

        List<String> rows = List.of(screen.split("\n")).filter(line -> line.startsWith("│"));
        assertFalse(screen.contains("HEAD detached"), screen);
        assertTrue(rows.head().contains("main"), screen);
        assertEquals(selecting("main"), selected);
    }

    @Test
    void onceABranchIsCheckedOutAgainItTakesTheHighlightAndItsLogShows() throws Exception {
        Sha at = new Sha("89eeda75aaaaaaaa");
        Model detached = detachedAt(at);
        Model onFeature = Update.update(
                        with(
                                detached,
                                // Not first, where the detached HEAD was: the highlight has to move to it.
                                List.of(
                                        Fixtures.branch(new BranchName("main"), false, new Sha("1")),
                                        Fixtures.branch(new BranchName("feature"), true, new Sha("2")),
                                        OLD)),
                        new LoadMsg.StatusLoaded(
                                new RepoStatus(Head.onBranch(new BranchName("feature"), new Sha("2")), List.empty())))
                .model();

        Option<Msg> selected;
        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(detached, BranchesPanel.PANE))) {
            RenderedText.of(testRunner, () -> panel.render(detached, BranchesPanel.PANE));
            assertEquals(
                    Option.of(new SelectionMsg.NoLogSelected(new NoLog.Detached(at))), panel.selectionChange(detached));
            RenderedText.of(testRunner, () -> panel.render(onFeature, BranchesPanel.PANE));
            selected = panel.selectionChange(onFeature);
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(selecting("feature"), selected);
        Model saidNoLog = Update.update(onFeature, new SelectionMsg.NoLogSelected(new NoLog.Detached(at)))
                .model();
        assertEquals(
                Option.none(),
                Update.update(saidNoLog, selected.get())
                        .model()
                        .repoView()
                        .branches()
                        .noLog());
    }

    @Test
    void theDetachedHeadListsTheFilesOfTheWorkingTree() throws Exception {
        // ADR 141: it is what is checked out.
        Model detached = detachedAt(new Sha("89eeda75aaaaaaaa"));
        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(detached, BranchesPanel.PANE))) {
            RenderedText.of(testRunner, () -> panel.render(detached, BranchesPanel.PANE));
            assertEquals(Option.of(new Followed.WorkingTree()), panel.followed(detached));
            SettledPilot.of(testRunner).quit();
        }
    }

    /**
     * {@link #before} once HEAD was detached at {@code at}: no branch is checked out.
     */
    private Model detachedAt(Sha at) {
        Model branches =
                with(before, List.of(Fixtures.branch(new BranchName("main"), false, new Sha("1")), FEATURE, OLD));
        return Update.update(branches, new LoadMsg.StatusLoaded(new RepoStatus(new Head.Detached(at), List.empty())))
                .model();
    }

    @Test
    void aReorderingWithoutCheckoutKeepsTheSameBranchHighlighted() throws Exception {
        Model after = with(before, List.of(MAIN, OLD, FEATURE));

        assertEquals(selecting("feature"), highlightedAfterMovingTo(1, after));
    }

    @Test
    void spinnerFramesBounceAsTimePasses() {
        java.util.Set<String> frames = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i ->
                        net.vanfleteren.jazygit.ui.text.TimeText.spinnerFrame(java.time.Instant.ofEpochMilli(i * 120L)))
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(
                java.util.Set.copyOf(
                        java.util.Arrays.asList(dev.tamboui.widgets.spinner.SpinnerStyle.BOUNCING_BAR.frames())),
                frames);
    }

    @Test
    void aBranchBeingPushedOrPulledShowsWhatIsDoneLeftOfTheSpinner() throws Exception {
        Model busy = TestModels.busy(
                TestModels.busy(before, new BranchRef.Local(new BranchName("feature")), PushCmd.PUSH),
                new BranchRef.Local(new BranchName("main")),
                PullCmd.PULL);
        String frame = net.vanfleteren.jazygit.ui.text.TimeText.spinnerFrame(java.time.Instant.EPOCH);
        BranchesPanel panel = new BranchesPanel(
                msg -> {}, tab -> {}, java.time.Clock.fixed(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(busy, BranchesPanel.PANE))) {
            String screen = RenderedText.of(testRunner, () -> panel.render(busy, BranchesPanel.PANE));
            assertTrue(screen.contains("feature  Pushing " + frame), screen);
            assertTrue(screen.contains("main  Pulling " + frame), screen);
        }
    }

    @Test
    void aBranchBeingDeletedShowsOnlyTheSpinner() throws Exception {
        Model deleting =
                TestModels.busy(before, new BranchRef.Local(new BranchName("feature")), DeleteBranchCmd.DELETE_BRANCH);
        String frame = net.vanfleteren.jazygit.ui.text.TimeText.spinnerFrame(java.time.Instant.EPOCH);
        BranchesPanel panel = new BranchesPanel(
                msg -> {}, tab -> {}, java.time.Clock.fixed(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC));
        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(deleting, BranchesPanel.PANE))) {
            String screen = RenderedText.of(testRunner, () -> panel.render(deleting, BranchesPanel.PANE));
            assertTrue(screen.contains("feature  " + frame), screen);
            assertFalse(screen.contains("main  " + frame), screen);
        }
    }

    @Test
    void ageIsShownInTheLargestFittingUnit() {
        assertEquals("now", net.vanfleteren.jazygit.ui.text.TimeText.age(java.time.Duration.ofSeconds(45)));
        assertEquals("1m", net.vanfleteren.jazygit.ui.text.TimeText.age(java.time.Duration.ofSeconds(60)));
        assertEquals("2m", net.vanfleteren.jazygit.ui.text.TimeText.age(java.time.Duration.ofSeconds(150)));
        assertEquals("5m", net.vanfleteren.jazygit.ui.text.TimeText.age(java.time.Duration.ofMinutes(5)));
        assertEquals("3h", net.vanfleteren.jazygit.ui.text.TimeText.age(java.time.Duration.ofHours(3)));
        assertEquals("3h", net.vanfleteren.jazygit.ui.text.TimeText.age(java.time.Duration.ofMinutes(181)));
        assertEquals("3h", net.vanfleteren.jazygit.ui.text.TimeText.age(java.time.Duration.ofMinutes(210)));
        assertEquals("3h", net.vanfleteren.jazygit.ui.text.TimeText.age(java.time.Duration.ofMinutes(239)));
        assertEquals("4h", net.vanfleteren.jazygit.ui.text.TimeText.age(java.time.Duration.ofMinutes(240)));
        assertEquals("2d", net.vanfleteren.jazygit.ui.text.TimeText.age(java.time.Duration.ofDays(2)));
    }

    @Test
    void aRenamedBranchStaysHighlightedUnderItsNewName() throws Exception {
        Model after = with(before, List.of(MAIN, OLD, Fixtures.branch(new BranchName("topic"), false, new Sha("2"))));

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
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.PANE))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.PANE));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).press('R');
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(
                java.util.List.of(new net.vanfleteren.jazygit.branches.branch.rename.RenameBranchMsg.Requested(
                        new BranchName("feature"))),
                dispatched);
    }

    @Test
    void nAsksForANewBranchStartingAtTheHighlightedBranch() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add, tab -> {});
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.PANE))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.PANE));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).press('n');
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(java.util.List.of(new NewBranchMsg.Requested(new BranchName("feature"))), dispatched);
    }

    @Test
    void shiftPPushesTheHighlightedBranch() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add, tab -> {});
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.PANE))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.PANE));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).press('P');
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(
                java.util.List.of(
                        new net.vanfleteren.jazygit.branches.branch.push.PushMsg.Requested(new BranchName("feature"))),
                dispatched);
    }

    @Test
    void pPullsTheHighlightedBranch() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add, tab -> {});
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.PANE))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.PANE));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).press('p');
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(
                java.util.List.of(
                        new net.vanfleteren.jazygit.branches.branch.pull.PullMsg.Requested(new BranchName("feature"))),
                dispatched);
    }

    @Test
    void questionMarkAsksForTheKeybindingsOfThePanel() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add, tab -> {});
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.PANE))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.PANE));
            SettledPilot.of(testRunner).press('?');
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(1, dispatched.size());
        assertTrue(
                dispatched.getFirst()
                                instanceof
                                net.vanfleteren.jazygit.chrome.help.HelpMsg.Requested(String scopeId, var actions)
                        && scopeId.equals(net.vanfleteren.jazygit.ui.keys.Scopes.BRANCHES.id())
                        && actions.head().isDefined(),
                dispatched.toString());
    }

    @Test
    void theRemoteTabListsTheRemoteBranchesUnderItsOwnTitle() throws Exception {
        Model remote = withRemotes(before);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(remote, BranchesPanel.PANE))) {
            String screen = RenderedText.of(testRunner, () -> panel.render(remote, BranchesPanel.PANE));
            SettledPilot.of(testRunner).quit();

            assertTrue(screen.contains("Branches │ Remotes"), screen);
            assertTrue(screen.contains("origin/feature"), screen);
            assertTrue(screen.contains("upstream/main"), screen);
            assertFalse(screen.contains(" old"), screen);
        }
        assertEquals(
                Option.of(new SelectionMsg.BranchSelected(
                        new BranchRef.Remote(new RemoteBranch("origin", new BranchName("feature"))))),
                panel.selectionChange(remote));
    }

    @Test
    void clickingARemoteBranchHighlightsIt() throws Exception {
        Model remote = withRemotes(before);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(remote, BranchesPanel.PANE))) {
            // The title, origin/feature on two lines, then upstream/main.
            SettledPilot.of(testRunner).mousePress(MouseButton.LEFT, 5, 3);
            SettledPilot.of(testRunner).quit();
        }
        assertEquals(
                Option.of(new SelectionMsg.BranchSelected(
                        new BranchRef.Remote(new RemoteBranch("upstream", new BranchName("main"))))),
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
                Option.of(new Head.OnBranch(new BranchName("other"), Option.of(new Sha("3")))),
                false,
                false,
                Option.none(),
                false);
        Model three = two.mapRepo(repo -> repo.withWorktrees(
                new Loadable.Loaded<>(repo.worktrees().toOption().get().append(added))));

        panel.render(two, BranchesPanel.PANE);
        panel.render(three, BranchesPanel.PANE);
        assertEquals(selecting("main"), panel.selectionChange(three));

        panel.render(three.openPopup(new AboutWorktree("/repos/app-other")), BranchesPanel.PANE);
        assertEquals(selecting("other"), panel.selectionChange(three));
    }

    @Test
    void theWorktreesTabListsTheWorktreesAndItsKeysActOnTheHighlightedOne() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add, tab -> {});
        Model worktrees = withWorktrees(before);
        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(worktrees, BranchesPanel.PANE))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            String screen = RenderedText.of(testRunner, () -> panel.render(worktrees, BranchesPanel.PANE));
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
                Option.of(new SelectionMsg.BranchSelected(new BranchRef.Local(new BranchName("feature")))),
                panel.selectionChange(worktrees));
    }

    @Test
    void onTheLocalTabWChecksTheHighlightedBranchOutInANewWorktree() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add, tab -> {});
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.PANE))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.PANE));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).press('w');
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(java.util.List.of(new AddWorktreeMsg.Requested(new BranchName("feature"))), dispatched);
    }

    @Test
    void theRemoteTabSaysWhenThereAreNoRemoteBranches() throws Exception {
        Model none = before.mapRepoView(
                        repoView -> repoView.mapBranches(branches -> branches.withTab(BranchesTab.REMOTE)))
                .mapRepo(repo -> repo.withRemoteBranches(new Loadable.Loaded<>(List.empty())));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(none, BranchesPanel.PANE))) {
            String screen = RenderedText.of(testRunner, () -> panel.render(none, BranchesPanel.PANE));
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
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(remote, BranchesPanel.PANE))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(remote, BranchesPanel.PANE));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).press(' ');
            SettledPilot.of(testRunner).press('n');
            SettledPilot.of(testRunner).press('[');
            SettledPilot.of(testRunner).press('P');
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).quit();
        }

        RemoteBranch upstreamMain = new RemoteBranch("upstream", new BranchName("main"));
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
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.PANE))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.PANE));
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
        Commit behind = Fixtures.commit(new Sha("b1"), "Ada Lovelace", "a@x", java.time.Instant.EPOCH, "Theirs", "");
        Commit newer = Fixtures.commit(new Sha("a2"), "Ada Lovelace", "a@x", java.time.Instant.EPOCH, "Ours two", "");
        Commit older = Fixtures.commit(new Sha("a1"), "Ada Lovelace", "a@x", java.time.Instant.EPOCH, "Ours one", "");
        RemoteBranch upstream = new RemoteBranch("origin", new BranchName("feature"));
        Model diverged = before.mapRepoView(repoView ->
                repoView.mapBranches(branches -> branches.withDivergence(Option.of(new DivergenceView(
                        new BranchName("feature"),
                        new BranchRef.Remote(upstream),
                        DivergenceView.Against.UPSTREAM,
                        new Loadable.Loaded<>(new Divergence(
                                new Divergence.Side(List.of(newer, older), 2),
                                new Divergence.Side(List.of(behind), 1))),
                        Option.none())))));
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        java.util.List<String> opened = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(
                dispatched::add,
                tab -> {},
                () -> opened.add("commit"),
                java.time.Clock.systemUTC(),
                Theme.Highlights.DEFAULT);
        java.util.List<Option<Msg>> highlighted = new java.util.ArrayList<>();
        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(diverged, BranchesPanel.PANE))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            String screen = RenderedText.ofLarge(testRunner, () -> panel.render(diverged, BranchesPanel.PANE));
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
                    ToolkitTestRunner.runTest(() -> sending.render(shown, BranchesPanel.PANE))) {
                SettledPilot.focus(testRunner, BranchesPanel.ID);
                RenderedText.of(testRunner, () -> sending.render(shown, BranchesPanel.PANE));
                SettledPilot.of(testRunner).press(KeyCode.ENTER);
                SettledPilot.of(testRunner).pause();
                SettledPilot.of(testRunner).quit();
            }
        }

        assertEquals(
                java.util.List.of(
                        new BranchCommitsMsg.Opened(new BranchRef.Local(new BranchName("main"))),
                        new BranchCommitsMsg.Opened(
                                new BranchRef.Remote(new RemoteBranch("origin", new BranchName("feature"))))),
                dispatched);
    }

    @Test
    void theCommitsOfABranchAreListedInPlaceOfTheBranchesUnderItsNameAndHowManyAreAhead() throws Exception {
        Commit newer = Fixtures.commit(new Sha("a2"), "Ada Lovelace", "a@x", java.time.Instant.EPOCH, "Ours", "");
        Commit older = Fixtures.commit(new Sha("a1"), "Ada Lovelace", "a@x", java.time.Instant.EPOCH, "Shared", "");
        BranchRef feature = new BranchRef.Local(new BranchName("feature"));
        BranchRef base = new BranchRef.Local(new BranchName("main"));
        Model open = before.mapRepoView(repoView -> repoView.mapBranches(
                        branches -> branches.withBranchCommits(Option.of(BranchCommitsView.of(feature))))
                .mapPreview(preview -> preview.withBranchLog(
                        Option.of(new Preview<>(feature, new Loadable.Loaded<>(List.of(newer, older))))))
                .mapPreview(preview -> preview.withBranchAhead(Option.of(new Preview<>(
                        feature,
                        new Loadable.Loaded<>(Option.of(
                                new Ahead(new Sha("a2"), base, List.of(newer), false, Option.of(older)))))))));
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        java.util.List<String> opened = new java.util.ArrayList<>();
        BranchesPanel sending = new BranchesPanel(
                dispatched::add,
                tab -> {},
                () -> opened.add("commit"),
                java.time.Clock.systemUTC(),
                Theme.Highlights.DEFAULT);
        java.util.List<Option<Msg>> highlighted = new java.util.ArrayList<>();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> sending.render(open, BranchesPanel.PANE))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            String screen = RenderedText.ofLarge(testRunner, () -> sending.render(open, BranchesPanel.PANE));
            highlighted.add(sending.selectionChange(open));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).pause();
            highlighted.add(sending.selectionChange(open));
            SettledPilot.of(testRunner).press(KeyCode.ENTER);
            SettledPilot.of(testRunner).pause();
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
        assertEquals(java.util.List.of(new BranchCommitsMsg.Closed(), new BranchesTabMsg.Switched(1)), dispatched);
        assertFalse(BranchesPanel.showsSpinner(TestModels.busy(open, feature, PushCmd.PUSH)));
    }

    /**
     * {@code before} listing the commits of {@code branch}, with {@code log} read as the log of {@code logOf}.
     */
    private Model listing(BranchRef branch, BranchRef logOf, List<Commit> log) {
        return before.mapRepoView(repoView -> repoView.mapBranches(
                        branches -> branches.withBranchCommits(Option.of(BranchCommitsView.of(branch))))
                .mapPreview(
                        preview -> preview.withBranchLog(Option.of(new Preview<>(logOf, new Loadable.Loaded<>(log))))));
    }

    @Test
    void aCommitOfTheBranchCopiedAfterItsCommitsWereDrawnStandsOut() {
        BranchRef feature = new BranchRef.Local(new BranchName("feature"));
        Commit copied = commit("a1", "Older");
        Model open = listing(feature, feature, List.of(commit("a2", "Ours"), copied));
        BranchCommitsList list = new BranchCommitsList(BranchesPanel.ID, msg -> {}, () -> {}, Theme.Highlights.DEFAULT);

        list.render(open, true, true);
        List<Option<String>> before =
                list.shownRows().map(row -> row.segs().head().cssClass());
        list.render(
                open.mapRepoView(repoView -> repoView.mapCommits(commits -> commits.withCherryPick(Option.of(copied)))),
                true,
                true);

        assertEquals(List.of(Option.none(), Option.none()), before);
        assertEquals(
                List.of(Option.none(), Option.of(Theme.Classes.COPIED)),
                list.shownRows().map(row -> row.segs().head().cssClass()));
    }

    private static Commit commit(String sha, String message) {
        return Fixtures.commit(new Sha(sha), "Ada Lovelace", "a@x", java.time.Instant.EPOCH, message, "");
    }

    @Test
    void theCommitsOfABranchOpenedAgainStartAtItsNewestEvenWhenTheOneBeforeHadTheHighlightedCommitToo()
            throws Exception {
        Commit shared = commit("a1", "Shared");
        BranchRef feature = new BranchRef.Local(new BranchName("feature"));
        BranchRef old = new BranchRef.Local(new BranchName("old"));
        java.util.concurrent.atomic.AtomicReference<Model> shown = new java.util.concurrent.atomic.AtomicReference<>(
                listing(feature, feature, List.of(commit("a2", "Ours"), shared)));
        Option<Msg> highlighted;
        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(shown.get(), BranchesPanel.PANE))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(shown.get(), BranchesPanel.PANE));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).pause();
            assertEquals(
                    Option.of(new SelectionMsg.BranchCommitSelected(shared.sha())), panel.selectionChange(shown.get()));
            shown.set(before);
            RenderedText.of(testRunner, () -> panel.render(shown.get(), BranchesPanel.PANE));
            shown.set(listing(old, old, List.of(commit("b1", "Theirs"), shared)));
            RenderedText.of(testRunner, () -> panel.render(shown.get(), BranchesPanel.PANE));
            highlighted = panel.selectionChange(shown.get());
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(Option.of(new SelectionMsg.BranchCommitSelected(new Sha("b1"))), highlighted);
    }

    @Test
    void theCommitsOfABranchWhoseLogIsStillReadShowThatAndThenStartAtItsNewest() throws Exception {
        BranchRef feature = new BranchRef.Local(new BranchName("feature"));
        BranchRef main = new BranchRef.Local(new BranchName("main"));
        java.util.concurrent.atomic.AtomicReference<Model> shown = new java.util.concurrent.atomic.AtomicReference<>(
                listing(feature, main, List.of(commit("c1", "On main"))));
        String loading;
        Option<Msg> whileLoading;
        Option<Msg> highlighted;
        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(shown.get(), BranchesPanel.PANE))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            loading = RenderedText.of(testRunner, () -> panel.render(shown.get(), BranchesPanel.PANE));
            whileLoading = panel.selectionChange(shown.get());
            shown.set(listing(feature, feature, List.of(commit("a2", "Ours"), commit("a1", "Older"))));
            RenderedText.of(testRunner, () -> panel.render(shown.get(), BranchesPanel.PANE));
            highlighted = panel.selectionChange(shown.get());
            SettledPilot.of(testRunner).quit();
        }

        assertFalse(loading.contains("On main"), loading);
        assertTrue(loading.contains(Placeholders.loading()), loading);
        assertEquals(Option.none(), whileLoading);
        assertEquals(Option.of(new SelectionMsg.BranchCommitSelected(new Sha("a2"))), highlighted);
    }

    /**
     * What the panel sends when the local branch {@code name} gets highlighted.
     */
    private static Option<Msg> selecting(String name) {
        return Option.of(new SelectionMsg.BranchSelected(new BranchRef.Local(new BranchName(name))));
    }

    private Option<Msg> highlightedAfterMovingTo(int index, Model after) throws Exception {
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.PANE))) {
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.PANE));
            for (int i = 0; i < index; i++) {
                SettledPilot.of(testRunner).press(KeyCode.DOWN);
            }
            SettledPilot.of(testRunner).pause();
            assertEquals(
                    selecting(List.of(MAIN, FEATURE, OLD).get(index).name().value()), panel.selectionChange(before));

            RenderedText.of(testRunner, () -> panel.render(after, BranchesPanel.PANE));
            SettledPilot.of(testRunner).quit();
            return panel.selectionChange(after);
        }
    }
}
