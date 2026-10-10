package net.vanfleteren.jazygit.ui;

import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.feature.branch.NewBranchMsg;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The highlighted row of the branches pane follows its branch when the list is reordered.
 */
class BranchesPanelTest {

    private static final Branch MAIN = new Branch("main", true, "1");
    private static final Branch FEATURE = new Branch("feature", false, "2");
    private static final Branch OLD = new Branch("old", false, "3");

    private final BranchesPanel panel = new BranchesPanel(msg -> { });
    private final Model before = with(TestModels.loaded(new SampleData()), List.of(MAIN, FEATURE, OLD));

    @Test
    void afterACheckoutTheNewCurrentBranchIsHighlighted() throws Exception {
        Model after = with(before, List.of(new Branch("old", true, "3"), new Branch("main", false, "1"), FEATURE));

        assertEquals("old", highlightedAfterMovingTo(2, after));
    }

    @Test
    void aReorderingWithoutCheckoutKeepsTheSameBranchHighlighted() throws Exception {
        Model after = with(before, List.of(MAIN, OLD, FEATURE));

        assertEquals("feature", highlightedAfterMovingTo(1, after));
    }

    @Test
    void syncShowsAGreenArrowForUnpushedAndARedOneForIncomingCommits() {
        java.time.Instant t = java.time.Instant.EPOCH;
        assertEquals(List.of(), BranchesPanel.sync(new Branch("b", false, "1", t, true, false, 0, 0)));
        assertEquals(List.of(LoadableList.Seg.colored(" ↑2", dev.tamboui.style.Color.GREEN)),
                BranchesPanel.sync(new Branch("b", false, "1", t, true, false, 2, 0)));
        assertEquals(List.of(LoadableList.Seg.colored(" ↓1", dev.tamboui.style.Color.RED)),
                BranchesPanel.sync(new Branch("b", false, "1", t, true, false, 0, 1)));
        assertEquals(List.of(LoadableList.Seg.colored(" ↑2", dev.tamboui.style.Color.GREEN),
                        LoadableList.Seg.colored(" ↓1", dev.tamboui.style.Color.RED)),
                BranchesPanel.sync(new Branch("b", false, "1", t, true, false, 2, 1)));
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
        List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.ID))) {
            testRunner.runner().focusManager().setFocus(BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.ID));
            testRunner.pilot().press(KeyCode.DOWN);
            testRunner.pilot().press('n');
            testRunner.pilot().pause();
            testRunner.pilot().quit();
        }

        assertEquals(List.of(new NewBranchMsg.Requested("feature")), dispatched);
    }

    @Test
    void shiftPPushesTheHighlightedBranch() throws Exception {
        List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.ID))) {
            testRunner.runner().focusManager().setFocus(BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.ID));
            testRunner.pilot().press(KeyCode.DOWN);
            testRunner.pilot().press('P');
            testRunner.pilot().pause();
            testRunner.pilot().quit();
        }

        assertEquals(List.of(new net.vanfleteren.jazygit.feature.branch.PushMsg.Requested("feature")), dispatched);
    }

    @Test
    void questionMarkAsksForTheKeybindingsOfThePanel() throws Exception {
        List<Msg> dispatched = new java.util.ArrayList<>();
        BranchesPanel panel = new BranchesPanel(dispatched::add);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, BranchesPanel.ID))) {
            testRunner.runner().focusManager().setFocus(BranchesPanel.ID);
            RenderedText.of(testRunner, () -> panel.render(before, BranchesPanel.ID));
            testRunner.pilot().press('?');
            testRunner.pilot().pause();
            testRunner.pilot().quit();
        }

        assertEquals(List.of(new net.vanfleteren.jazygit.feature.help.HelpMsg.Requested(
                net.vanfleteren.jazygit.feature.help.HelpTopic.BRANCHES)), dispatched);
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
            return panel.selectedBranch().orElseThrow();
        }
    }

    private static Model with(Model model, List<Branch> branches) {
        return Update.update(model, new LoadMsg.BranchesLoaded(branches)).model();
    }
}
