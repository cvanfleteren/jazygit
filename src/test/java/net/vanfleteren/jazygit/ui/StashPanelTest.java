package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.ArrayList;
import net.vanfleteren.jazygit.feature.help.HelpMsg;
import net.vanfleteren.jazygit.feature.stash.StashMsg;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import org.junit.jupiter.api.Test;

class StashPanelTest {

    private static Model withStashes(List<Stash> stashes) {
        return Update.update(TestModels.loaded(), new LoadMsg.StashesLoaded(stashes))
                .model();
    }

    @Test
    void listsTheStashes() throws Exception {
        Model model = withStashes(List.of(
                new Stash("stash@{0}", new Sha("s0"), "WIP on main abc1234 Add widget"),
                new Stash("stash@{1}", new Sha("s1"), "older")));
        StashPanel panel = new StashPanel(msg -> {});

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
        Stash older = new Stash("stash@{1}", new Sha("s1"), "older");
        Model model = withStashes(List.of(new Stash("stash@{0}", new Sha("s0"), "newest"), older));
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

            assertEquals(
                    java.util.List.of(
                            new StashMsg.PopRequested(older),
                            new StashMsg.DropRequested(older),
                            new HelpMsg.Requested(
                                    Scopes.STASH.id(), Keymap.standard().actions(Scopes.STASH, Option.of(older)))),
                    dispatched);
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
        Model model =
                withStashes(List.range(0, 80).map(i -> new Stash("stash@{" + i + "}", new Sha("s" + i), "work " + i)));
        StashPanel panel = new StashPanel(msg -> {});

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, StashPanel.ID))) {
            testRunner.runner().focusManager().setFocus(StashPanel.ID);
            testRunner.pilot().pause();
            for (int i = 0; i < 45; i++) {
                testRunner.pilot().press(KeyCode.DOWN);
            }
            testRunner.pilot().pause();

            String screen = RenderedText.ofLarge(testRunner, () -> panel.render(model, StashPanel.ID));
            assertEquals("work 45", panel.selectedStash().get().message());
            assertTrue(screen.contains("45 work 45"), screen);
            assertFalse(screen.contains("0 work 0"), screen);
            testRunner.pilot().quit();
        }
    }

    @Test
    void theHighlightOnlyShowsWhileThePaneIsInUseAndComesBackWhereItWas() throws Exception {
        Model model = withStashes(List.of(
                new Stash("stash@{0}", new Sha("s0"), "newest"), new Stash("stash@{1}", new Sha("s1"), "older")));
        StashPanel panel = new StashPanel(msg -> {});

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, StashPanel.ID))) {
            testRunner.runner().focusManager().setFocus(StashPanel.ID);
            testRunner.pilot().pause();
            testRunner.pilot().press(KeyCode.DOWN);
            testRunner.pilot().pause();

            String inactive =
                    RenderedText.ofLarge(testRunner, () -> panel.render(model, CommitsPanel.ID, CommitsPanel.ID));
            assertTrue(inactive.contains("  0 newest"), inactive);
            assertTrue(inactive.contains("  1 older"), inactive);
            assertFalse(inactive.contains(">"), inactive);

            // Still in use while, say, a popup opened from it has the focus.
            String active = RenderedText.ofLarge(testRunner, () -> panel.render(model, null, StashPanel.ID));
            assertTrue(active.contains("> 1 older"), active);
            assertEquals("older", panel.selectedStash().get().message());
            testRunner.pilot().quit();
        }
    }
}
