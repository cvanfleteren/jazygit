package net.vanfleteren.jazygit;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.tui.pilot.Pilot;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.CommitsPanel;
import net.vanfleteren.jazygit.ui.ContentPanel;
import net.vanfleteren.jazygit.ui.FilesPanel;
import net.vanfleteren.jazygit.ui.StatusPanel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.row;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private record Fixture(SampleData data, Model model, List<Msg> dispatched, List<Msg> selections, FilesPanel files,
                            BranchesPanel branches, CommitsPanel commits,
                            AtomicReference<FocusManager> focusManagerRef) {

        private Fixture() {
            this(new SampleData(), new ArrayList<>());
        }

        private Fixture(SampleData data, List<Msg> dispatched) {
            this(data, TestModels.loaded(data), dispatched, new ArrayList<>(), new FilesPanel(), new BranchesPanel(dispatched::add),
                    new CommitsPanel(), new AtomicReference<>());
        }

        Supplier<Element> renderer() {
            return () -> {
                FocusManager focusManager = focusManagerRef.get();
                String focusedId = focusManager == null ? null : focusManager.focusedId();
                Panel branchesPane = branches.render(model, focusedId);
                branches.selectionChange(model).ifPresent(selections::add);
                return row(
                        column(StatusPanel.render(model), files.render(model, focusedId),
                                branchesPane, commits.render(model, focusedId))
                                .percent(30),
                        ContentPanel.render(model, focusedId, commits.selectedIndex())
                                .fill());
            };
        }
    }

    @Test
    void tabCyclesFocusThroughAllThreePanesAndWrapsAround() throws Exception {
        Fixture fixture = new Fixture();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(fixture.renderer())) {
            FocusManager focusManager = testRunner.runner().focusManager();
            fixture.focusManagerRef().set(focusManager);
            focusManager.setFocus(FilesPanel.ID);

            Pilot pilot = testRunner.pilot();
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
            assertEquals(FilesPanel.ID, focusManager.focusedId(), "Tab should wrap back to Files");

            pilot.press(KeyCode.TAB, KeyModifiers.SHIFT);
            pilot.pause();
            assertEquals(CommitsPanel.ID, focusManager.focusedId(), "Shift+Tab should cycle in reverse");

            pilot.press(KeyCode.TAB, KeyModifiers.SHIFT);
            pilot.pause();
            assertEquals(BranchesPanel.ID, focusManager.focusedId());

            pilot.quit();
        }
    }

    @Test
    void upDownNavigatesSelectionWithinFocusedPaneAndClampsAtBoundaries() throws Exception {
        Fixture fixture = new Fixture();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(fixture.renderer())) {
            FocusManager focusManager = testRunner.runner().focusManager();
            fixture.focusManagerRef().set(focusManager);
            focusManager.setFocus(BranchesPanel.ID);

            Pilot pilot = testRunner.pilot();
            pilot.pause();
            assertEquals(0, fixture.branches().selectedIndex());

            pilot.press(KeyCode.UP);
            pilot.pause();
            assertEquals(0, fixture.branches().selectedIndex(), "selection should not go below the first item");

            pilot.press(KeyCode.DOWN);
            pilot.pause();
            assertEquals(1, fixture.branches().selectedIndex());

            int branchCount = fixture.data().branches().size();
            for (int i = 0; i < branchCount + 5; i++) {
                pilot.press(KeyCode.DOWN);
            }
            pilot.pause();
            assertEquals(branchCount - 1, fixture.branches().selectedIndex(), "selection should clamp at the last item");

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
            focusManager.setFocus(BranchesPanel.ID);

            Pilot pilot = testRunner.pilot();
            pilot.pause();
            assertEquals(new Msg.BranchSelected("main"), fixture.selections().getLast());

            pilot.press(KeyCode.DOWN);
            pilot.pause();
            assertEquals(new Msg.BranchSelected("feature/initial-layout"), fixture.selections().getLast());

            pilot.quit();
        }
    }

    @Test
    void spaceRequestsCheckoutOfTheHighlightedBranch() throws Exception {
        Fixture fixture = new Fixture();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(fixture.renderer())) {
            FocusManager focusManager = testRunner.runner().focusManager();
            fixture.focusManagerRef().set(focusManager);
            focusManager.setFocus(BranchesPanel.ID);

            Pilot pilot = testRunner.pilot();
            pilot.pause();
            pilot.press(KeyCode.DOWN);
            pilot.press(' ');
            pilot.pause();
            assertEquals(List.of(new Msg.CheckoutRequested("feature/initial-layout")), fixture.dispatched());

            focusManager.setFocus(CommitsPanel.ID);
            pilot.pause();
            pilot.press(' ');
            pilot.pause();
            assertEquals(1, fixture.dispatched().size(), "Space outside the branches pane should not check out");

            pilot.quit();
        }
    }
}
