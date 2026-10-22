package net.vanfleteren.jazygit;

import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.row;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.tui.pilot.Pilot;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.feature.branch.checkout.CheckoutMsg;
import net.vanfleteren.jazygit.feature.commit.create.CommitMsg;
import net.vanfleteren.jazygit.feature.commit.reword.RewordMsg;
import net.vanfleteren.jazygit.feature.stage.StageMsg;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.ui.*;
import net.vanfleteren.jazygit.ui.SettledPilot;
import org.junit.jupiter.api.Test;

/**
 * Pilot-driven integration tests exercising the same layout/composition as
 * {@link JazygitApp#render()}: focus cycling across the three left panes (Tab/Shift+Tab),
 * in-list Up/Down selection navigation clamped to list boundaries, and Space checking out the
 * highlighted branch.
 *
 * <p>These tests build the layout directly from the real {@code ui} panels (rather than
 * running {@link JazygitApp} itself) because {@link dev.tamboui.toolkit.app.ToolkitApp#run()}
 * always opens a real terminal; {@link ToolkitTestRunner} is the headless entry point instead.
 */
class JazygitAppPilotTest {

    private static final ContentPanel CONTENT = new ContentPanel(Theme.SpanStyles.NONE, () -> false);

    @Test
    void tabCyclesFocusThroughAllFourPanesAndWrapsAround() throws Exception {
        Fixture fixture = new Fixture();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(fixture.renderer())) {
            FocusManager focusManager = testRunner.runner().focusManager();
            fixture.focusManagerRef().set(focusManager);
            SettledPilot.focus(testRunner, FilesPanel.ID);

            Pilot pilot = SettledPilot.of(testRunner);
            pilot.pause();
            assertEquals(FilesPanel.ID, focusManager.focusedId());

            pilot.press(KeyCode.TAB);
            pilot.pause();
            assertEquals(BranchesPanel.ID, focusManager.focusedId());

            pilot.press(KeyCode.TAB);
            pilot.pause();
            assertEquals(CommitsPanel.ID, focusManager.focusedId());

            pilot.press(KeyCode.TAB);
            pilot.pause();
            assertEquals(StashPanel.ID, focusManager.focusedId());

            pilot.press(KeyCode.TAB);
            pilot.pause();
            assertEquals(FilesPanel.ID, focusManager.focusedId(), "Tab should wrap back to Files");

            pilot.press(KeyCode.TAB, KeyModifiers.SHIFT);
            pilot.pause();
            assertEquals(StashPanel.ID, focusManager.focusedId(), "Shift+Tab should cycle in reverse");

            pilot.press(KeyCode.TAB, KeyModifiers.SHIFT);
            pilot.pause();
            assertEquals(CommitsPanel.ID, focusManager.focusedId());

            pilot.quit();
        }
    }

    @Test
    void theDiffAreasAreOnlyPartOfTheTabCycleOnceTheyHaveTheFocus() throws Exception {
        SampleData data = new SampleData();
        Model loaded = TestModels.loaded(data);
        io.vavr.collection.List<FileEntry> files =
                loaded.repo().status().toOption().get().files();
        Model model = Update.update(
                        Update.update(loaded, new SelectionMsg.FilesSelected(files))
                                .model(),
                        new LoadMsg.FileDiffLoaded(files, DiffOptions.DEFAULT, new Diffs("+staged\n", "+unstaged\n")))
                .model();
        java.util.List<Msg> dispatched = new ArrayList<>();
        Fixture fixture = new Fixture(
                data,
                model,
                dispatched,
                new ArrayList<>(),
                new FilesPanel(dispatched::add, () -> {}),
                new BranchesPanel(dispatched::add, tab -> {}),
                new CommitsPanel(dispatched::add, () -> {}),
                new StashPanel(dispatched::add),
                new AtomicReference<>());

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(fixture.renderer())) {
            FocusManager focusManager = testRunner.runner().focusManager();
            fixture.focusManagerRef().set(focusManager);
            SettledPilot.focus(testRunner, StashPanel.ID);

            Pilot pilot = SettledPilot.of(testRunner);
            pilot.pause();
            pilot.press(KeyCode.TAB);
            pilot.pause();
            assertEquals(FilesPanel.ID, focusManager.focusedId(), "Tab from the last pane should skip the diffs");

            // As Enter on a file does; the key after it renders the diffs with the focus.
            SettledPilot.focus(testRunner, "diff-staged");
            pilot.press(KeyCode.DOWN);
            pilot.pause();
            pilot.press(KeyCode.TAB);
            pilot.pause();
            assertEquals("diff-unstaged", focusManager.focusedId(), "Tab should go from one diff to the other");

            pilot.quit();
        }
    }

    @Test
    void theRemoteBranchesAreOneStopOfTheTabCycleAndTheirKeysWorkThere() throws Exception {
        SampleData data = new SampleData();
        java.util.List<Msg> dispatched = new ArrayList<>();
        RemoteBranch feature = new RemoteBranch("origin", new Branch.Name("feature"));
        Model remote = TestModels.loaded(data)
                .mapRepoView(repoView -> repoView.withBranchesTab(BranchesTab.REMOTE))
                .mapRepo(repo -> repo.withRemoteBranches(new Loadable.Loaded<>(List.of(
                        new RemoteRef(feature, new Sha("0a1b2c3d4e5f60718293a4b5c6d7e8f901234567"), Instant.EPOCH)))));
        Fixture fixture = new Fixture(
                data,
                remote,
                dispatched,
                new ArrayList<>(),
                new FilesPanel(dispatched::add, () -> {}),
                new BranchesPanel(dispatched::add, tab -> {}),
                new CommitsPanel(dispatched::add, () -> {}),
                new StashPanel(dispatched::add),
                new AtomicReference<>());

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(fixture.renderer())) {
            FocusManager focusManager = testRunner.runner().focusManager();
            fixture.focusManagerRef().set(focusManager);
            SettledPilot.focus(testRunner, FilesPanel.ID);

            Pilot pilot = SettledPilot.of(testRunner);
            pilot.press(KeyCode.TAB);
            pilot.pause();
            assertEquals(BranchesPanel.ID, focusManager.focusedId());
            assertEquals(
                    new SelectionMsg.BranchSelected(new BranchRef.Remote(feature)),
                    fixture.selections().getLast());

            pilot.press(' ');
            pilot.press(KeyCode.TAB);
            pilot.pause();
            assertEquals(CommitsPanel.ID, focusManager.focusedId());

            pilot.quit();
        }
        assertEquals(java.util.List.of(new CheckoutMsg.RemoteRequested(feature)), dispatched);
    }

    @Test
    void upDownNavigatesSelectionWithinFocusedPaneAndClampsAtBoundaries() throws Exception {
        Fixture fixture = new Fixture();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(fixture.renderer())) {
            FocusManager focusManager = testRunner.runner().focusManager();
            fixture.focusManagerRef().set(focusManager);
            SettledPilot.focus(testRunner, BranchesPanel.ID);

            Pilot pilot = SettledPilot.of(testRunner);
            pilot.pause();
            assertEquals(fixture.selecting(0), fixture.branches().selectionChange(fixture.model()));

            pilot.press(KeyCode.UP);
            pilot.pause();
            assertEquals(
                    fixture.selecting(0),
                    fixture.branches().selectionChange(fixture.model()),
                    "selection should not go below the first item");

            pilot.press(KeyCode.DOWN);
            pilot.pause();
            assertEquals(fixture.selecting(1), fixture.branches().selectionChange(fixture.model()));

            int branchCount = fixture.data().branches().get().size();
            for (int i = 0; i < branchCount + 5; i++) {
                pilot.press(KeyCode.DOWN);
            }
            pilot.pause();
            assertEquals(
                    fixture.selecting(branchCount - 1),
                    fixture.branches().selectionChange(fixture.model()),
                    "selection should clamp at the last item");

            assertEquals(0, fixture.commits().selectedIndex(), "an unfocused pane's selection should be unaffected");

            pilot.quit();
        }
    }

    @Test
    void movingTheHighlightSelectsTheBranchWhoseLogIsShown() throws Exception {
        Fixture fixture = new Fixture();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(fixture.renderer())) {
            FocusManager focusManager = testRunner.runner().focusManager();
            fixture.focusManagerRef().set(focusManager);
            SettledPilot.focus(testRunner, BranchesPanel.ID);

            Pilot pilot = SettledPilot.of(testRunner);
            pilot.pause();
            assertEquals(
                    new SelectionMsg.BranchSelected(new BranchRef.Local(new Branch.Name("main"))),
                    fixture.selections().getLast());

            pilot.press(KeyCode.DOWN);
            pilot.pause();
            assertEquals(
                    new SelectionMsg.BranchSelected(new BranchRef.Local(new Branch.Name("feature/initial-layout"))),
                    fixture.selections().getLast());

            pilot.quit();
        }
    }

    @Test
    void spaceRequestsCheckoutOfTheHighlightedBranch() throws Exception {
        Fixture fixture = new Fixture();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(fixture.renderer())) {
            FocusManager focusManager = testRunner.runner().focusManager();
            fixture.focusManagerRef().set(focusManager);
            SettledPilot.focus(testRunner, BranchesPanel.ID);

            Pilot pilot = SettledPilot.of(testRunner);
            pilot.pause();
            pilot.press(KeyCode.DOWN);
            pilot.press(' ');
            pilot.pause();
            assertEquals(
                    java.util.List.of(new CheckoutMsg.Requested(new Branch.Name("feature/initial-layout"))),
                    fixture.dispatched());

            SettledPilot.focus(testRunner, CommitsPanel.ID);
            pilot.pause();
            pilot.press(' ');
            pilot.pause();
            assertEquals(1, fixture.dispatched().size(), "Space outside the branches pane should not check out");

            pilot.quit();
        }
    }

    @Test
    void rRequestsARewordFromTheCommitsPane() throws Exception {
        Fixture fixture = new Fixture();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(fixture.renderer())) {
            FocusManager focusManager = testRunner.runner().focusManager();
            fixture.focusManagerRef().set(focusManager);
            SettledPilot.focus(testRunner, CommitsPanel.ID);

            Pilot pilot = SettledPilot.of(testRunner);
            pilot.pause();
            // Moving re-renders, which is how the panel learns it has focus.
            pilot.press(KeyCode.DOWN);
            pilot.press(KeyCode.UP);
            pilot.pause();
            pilot.press('r');
            pilot.pause();

            assertTrue(fixture.dispatched()
                    .contains(new RewordMsg.Requested(
                            fixture.data().commits().get().head().sha())));

            pilot.quit();
        }
    }

    @Test
    void cRequestsACommitFromTheFilesPane() throws Exception {
        Fixture fixture = new Fixture();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(fixture.renderer())) {
            FocusManager focusManager = testRunner.runner().focusManager();
            fixture.focusManagerRef().set(focusManager);
            SettledPilot.focus(testRunner, FilesPanel.ID);

            Pilot pilot = SettledPilot.of(testRunner);
            pilot.pause();
            // Moving re-renders, which is how the panel learns it has focus.
            pilot.press(KeyCode.DOWN);
            pilot.press(KeyCode.UP);
            pilot.pause();
            pilot.press('c');
            pilot.pause();

            assertTrue(fixture.dispatched().contains(new CommitMsg.Requested()));

            pilot.quit();
        }
    }

    @Test
    void spaceTogglesStagingOfTheSelectedFilesNode() throws Exception {
        Fixture fixture = new Fixture();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(fixture.renderer())) {
            FocusManager focusManager = testRunner.runner().focusManager();
            fixture.focusManagerRef().set(focusManager);
            SettledPilot.focus(testRunner, FilesPanel.ID);

            Pilot pilot = SettledPilot.of(testRunner);
            pilot.pause();
            // Moving re-renders, which is how the panel learns it has focus.
            pilot.press(KeyCode.DOWN);
            pilot.press(KeyCode.UP);
            pilot.pause();
            pilot.press(' ');
            pilot.pause();

            assertEquals(1, fixture.dispatched().size());
            StageMsg.Requested all = (StageMsg.Requested) fixture.dispatched().get(0);
            // The virtual root is selected first, so every changed file is requested.
            assertEquals(
                    fixture.data().status().get().files().toSet(), all.files().toSet());

            // Files come before directories: README.md, notes.txt, pom.xml, the merged src/... directory,
            // JazygitApp.java and then the "model" directory.
            for (int i = 0; i < 6; i++) {
                pilot.press(KeyCode.DOWN);
            }
            pilot.press(' ');
            pilot.pause();

            StageMsg.Requested requested =
                    (StageMsg.Requested) fixture.dispatched().get(1);
            // Only the file in the highlighted "model" directory is requested.
            assertEquals(
                    List.of("src/main/java/net/vanfleteren/jazygit/model/SampleData.java"),
                    requested.files().map(f -> f.path()).toList());

            pilot.quit();
        }
    }

    private record Fixture(
            SampleData data,
            Model model,
            java.util.List<Msg> dispatched,
            java.util.List<Msg> selections,
            FilesPanel files,
            BranchesPanel branches,
            CommitsPanel commits,
            StashPanel stash,
            AtomicReference<FocusManager> focusManagerRef) {

        private Fixture() {
            this(new SampleData(), new ArrayList<>());
        }

        private Fixture(SampleData data, java.util.List<Msg> dispatched) {
            this(
                    data,
                    TestModels.loaded(data),
                    dispatched,
                    new ArrayList<>(),
                    new FilesPanel(dispatched::add, () -> {}),
                    new BranchesPanel(dispatched::add, tab -> {}),
                    new CommitsPanel(dispatched::add, () -> {}),
                    new StashPanel(dispatched::add),
                    new AtomicReference<>());
        }

        /**
         * What the branches pane sends when the {@code index}th branch gets highlighted.
         */
        Option<Msg> selecting(int index) {
            return Option.of(new SelectionMsg.BranchSelected(
                    new BranchRef.Local(data.branches().get().get(index).name())));
        }

        Supplier<Element> renderer() {
            return () -> {
                FocusManager focusManager = focusManagerRef.get();
                String focusedId = focusManager == null ? null : focusManager.focusedId();
                Panel branchesPane = branches.render(model, focusedId);
                branches.selectionChange(model).forEach(selections::add);
                return row(
                        column(
                                        StatusPanel.render(model),
                                        files.render(model, focusedId).percent(25),
                                        branchesPane.percent(20),
                                        commits.render(model, focusedId).percent(30),
                                        stash.render(model, focusedId).fill())
                                .percent(30),
                        CONTENT.render(model, focusedId).fill());
            };
        }
    }
}
