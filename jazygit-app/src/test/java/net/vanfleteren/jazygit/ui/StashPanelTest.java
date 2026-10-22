package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.ArrayList;
import net.vanfleteren.jazygit.feature.help.HelpMsg;
import net.vanfleteren.jazygit.feature.stash.StashMsg;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.keys.Keymap;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.*;
import org.junit.jupiter.api.Test;

class StashPanelTest {

    private static Model withStashes(List<Stash> stashes) {
        return Update.update(TestModels.loaded(), new LoadMsg.StashesLoaded(stashes))
                .model();
    }

    @Test
    void listsTheStashes() throws Exception {
        Model model = withStashes(List.of(
                new Stash("stash@{0}", new Sha("s0"), "WIP on main abc1234 Add widget", Option.none(), Instant.EPOCH),
                new Stash("stash@{1}", new Sha("s1"), "older", Option.none(), Instant.EPOCH)));
        StashPanel panel = new StashPanel(msg -> {});

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, null))) {
            String screen = RenderedText.ofLarge(testRunner, () -> panel.render(model, null));
            assertTrue(screen.contains("Stash"), screen);
            assertTrue(screen.contains("0 WIP on main abc1234 Add widget"), screen);
            assertTrue(screen.contains("1 older"), screen);

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void gUnstashesAndDDeletesTheHighlightedStashAndQuestionMarkShowsHelp() throws Exception {
        Stash older = new Stash("stash@{1}", new Sha("s1"), "older", Option.none(), Instant.EPOCH);
        Model model = withStashes(
                List.of(new Stash("stash@{0}", new Sha("s0"), "newest", Option.none(), Instant.EPOCH), older));
        java.util.List<Msg> dispatched = new ArrayList<>();
        StashPanel panel = new StashPanel(dispatched::add);

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, StashPanel.ID))) {
            SettledPilot.focus(testRunner, StashPanel.ID);
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).press('g');
            SettledPilot.of(testRunner).press('d');
            SettledPilot.of(testRunner).press('?');
            SettledPilot.of(testRunner).pause();

            assertEquals(
                    java.util.List.of(
                            new StashMsg.PopRequested(older),
                            new StashMsg.DropRequested(older),
                            new HelpMsg.Requested(
                                    Scopes.STASH.id(), Keymap.standard().actions(Scopes.STASH, Option.of(older)))),
                    dispatched);
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void theHighlightedStashIsReportedUntilItsChangesAreShownAndEnterOpensThem() throws Exception {
        Stash older = new Stash("stash@{1}", new Sha("s1"), "older", Option.none(), Instant.EPOCH);
        Model model = withStashes(
                List.of(new Stash("stash@{0}", new Sha("s0"), "newest", Option.none(), Instant.EPOCH), older));
        int[] opened = {0};
        StashPanel panel = new StashPanel(msg -> {}, () -> opened[0]++, Theme.Highlights.DEFAULT);

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, StashPanel.ID))) {
            SettledPilot.focus(testRunner, StashPanel.ID);
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).pause();

            Msg selected = panel.selectionChange(model).get();
            assertEquals(new SelectionMsg.StashSelected(older.sha()), selected);
            assertEquals(
                    Option.none(),
                    panel.selectionChange(Update.update(model, selected).model()));

            SettledPilot.of(testRunner).press(KeyCode.ENTER);
            SettledPilot.of(testRunner).pause();
            assertEquals(1, opened[0]);
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void gAndDDoNothingWithoutStashes() throws Exception {
        Model model = withStashes(List.of());
        java.util.List<Msg> dispatched = new ArrayList<>();
        StashPanel panel = new StashPanel(dispatched::add);

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, StashPanel.ID))) {
            SettledPilot.focus(testRunner, StashPanel.ID);
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).press('g');
            SettledPilot.of(testRunner).press('d');
            SettledPilot.of(testRunner).pause();

            assertEquals(java.util.List.of(), dispatched);
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void theListScrollsAlongWithTheSelection() throws Exception {
        Model model = withStashes(List.range(0, 80)
                .map(i -> new Stash("stash@{" + i + "}", new Sha("s" + i), "work " + i, Option.none(), Instant.EPOCH)));
        StashPanel panel = new StashPanel(msg -> {});

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, StashPanel.ID))) {
            SettledPilot.focus(testRunner, StashPanel.ID);
            SettledPilot.of(testRunner).pause();
            for (int i = 0; i < 45; i++) {
                SettledPilot.of(testRunner).press(KeyCode.DOWN);
            }
            SettledPilot.of(testRunner).pause();

            String screen = RenderedText.ofLarge(testRunner, () -> panel.render(model, StashPanel.ID));
            assertEquals("work 45", panel.selectedStash().get().message());
            assertTrue(screen.contains("45 work 45"), screen);
            assertFalse(screen.contains("0 work 0"), screen);
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void theHighlightOnlyShowsWhileThePaneIsInUseAndComesBackWhereItWas() throws Exception {
        Model model = withStashes(List.of(
                new Stash("stash@{0}", new Sha("s0"), "newest", Option.none(), Instant.EPOCH),
                new Stash("stash@{1}", new Sha("s1"), "older", Option.none(), Instant.EPOCH)));
        StashPanel panel = new StashPanel(msg -> {});

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, StashPanel.ID))) {
            SettledPilot.focus(testRunner, StashPanel.ID);
            SettledPilot.of(testRunner).pause();
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).pause();

            String inactive =
                    RenderedText.ofLarge(testRunner, () -> panel.render(model, CommitsPanel.ID, CommitsPanel.ID));
            assertTrue(inactive.contains("  0 newest"), inactive);
            assertTrue(inactive.contains("  1 older"), inactive);
            assertFalse(inactive.contains(">"), inactive);

            // Still in use while, say, a popup opened from it has the focus.
            String active = RenderedText.ofLarge(testRunner, () -> panel.render(model, null, StashPanel.ID));
            assertTrue(active.contains("> 1 older"), active);
            assertEquals("older", panel.selectedStash().get().message());
            SettledPilot.of(testRunner).quit();
        }
    }
}
