package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.help.HelpMsg;
import net.vanfleteren.jazygit.feature.stash.StashMsg;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StashPanelTest {

    private static Model withStashes(List<Stash> stashes) {
        return Update.update(TestModels.loaded(), new LoadMsg.StashesLoaded(stashes)).model();
    }

    @Test
    void listsTheStashes() throws Exception {
        Model model = withStashes(List.of(new Stash("stash@{0}", "WIP on main abc1234 Add widget"),
                new Stash("stash@{1}", "older")));
        StashPanel panel = new StashPanel(msg -> { });

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, null))) {
            String screen = RenderedText.ofLarge(testRunner, () -> panel.render(model, null));
            assertTrue(screen.contains("Stash"), screen);
            assertTrue(screen.contains("0 WIP on main abc1234 Add widget"), screen);
            assertTrue(screen.contains("1 older"), screen);

            testRunner.pilot().quit();
        }
    }

    @Test
    void gUnstashesAndDDeletesTheHighlightedStashAndQuestionMarkShowsHelp() throws Exception {
        Model model = withStashes(List.of(new Stash("stash@{0}", "newest"), new Stash("stash@{1}", "older")));
        java.util.List<Msg> dispatched = new ArrayList<>();
        StashPanel panel = new StashPanel(dispatched::add);

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, StashPanel.ID))) {
            testRunner.runner().focusManager().setFocus(StashPanel.ID);
            testRunner.pilot().pause();
            testRunner.pilot().press(KeyCode.DOWN);
            testRunner.pilot().pause();
            testRunner.pilot().press('g');
            testRunner.pilot().press('d');
            testRunner.pilot().press('?');
            testRunner.pilot().pause();

            assertEquals(java.util.List.of(new StashMsg.PopRequested("stash@{1}"), new StashMsg.DropRequested("stash@{1}"),
                    new HelpMsg.Requested(Scopes.STASH.id())), dispatched);
            testRunner.pilot().quit();
        }
    }

    @Test
    void gAndDDoNothingWithoutStashes() throws Exception {
        Model model = withStashes(List.of());
        java.util.List<Msg> dispatched = new ArrayList<>();
        StashPanel panel = new StashPanel(dispatched::add);

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, StashPanel.ID))) {
            testRunner.runner().focusManager().setFocus(StashPanel.ID);
            testRunner.pilot().pause();
            testRunner.pilot().press('g');
            testRunner.pilot().press('d');
            testRunner.pilot().pause();

            assertEquals(java.util.List.of(), dispatched);
            testRunner.pilot().quit();
        }
    }

    @Test
    void theListScrollsAlongWithTheSelection() throws Exception {
        Model model = withStashes(List.range(0, 80).map(i -> new Stash("stash@{" + i + "}", "work " + i)));
        StashPanel panel = new StashPanel(msg -> { });

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, StashPanel.ID))) {
            testRunner.runner().focusManager().setFocus(StashPanel.ID);
            testRunner.pilot().pause();
            for (int i = 0; i < 45; i++) {
                testRunner.pilot().press(KeyCode.DOWN);
            }
            testRunner.pilot().pause();

            String screen = RenderedText.ofLarge(testRunner, () -> panel.render(model, StashPanel.ID));
            assertEquals("work 45", panel.selectedStash().orElseThrow().message());
            assertTrue(screen.contains("45 work 45"), screen);
            assertFalse(screen.contains("0 work 0"), screen);
            testRunner.pilot().quit();
        }
    }
}
