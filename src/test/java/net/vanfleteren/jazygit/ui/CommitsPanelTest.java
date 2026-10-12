package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.CommitGraph;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommitsPanelTest {

    private static Commit commit(String sha, String... parents) {
        return new Commit(sha + "0000", sha, "Ada", "ada@example.com", Instant.EPOCH, "msg " + sha, "",
                java.util.Arrays.stream(parents).map(p -> p + "0000").toList());
    }

    @Test
    void mergesAreFollowedByTheirBranchWithRails() {
        List<Commit> log = CommitGraph.arrange(List.of(commit("mmmmmmm", "bbbbbbb", "fffffff2"),
                commit("fffffff2", "fffffff1"), commit("bbbbbbb", "aaaaaaa"), commit("fffffff1", "aaaaaaa"),
                commit("aaaaaaa")));

        List<LoadableList.Row> rows = CommitsPanel.rows(CommitGraph.layout(log));

        assertEquals(List.of("mmmmmmm Ad ◎─╮ msg mmmmmmm", "fffffff2 Ad │ ○ msg fffffff2", "fffffff1 Ad │ ○ msg fffffff1",
                "bbbbbbb Ad ○─╯ msg bbbbbbb", "aaaaaaa Ad ○ msg aaaaaaa"), rows.stream().map(LoadableList.Row::text).toList());
    }

    @Test
    void initialsAreTheCapitalizedFirstLettersOfTheName() {
        assertEquals("CV", CommitsPanel.initials("christophe vanfleteren"));
        assertEquals("AL", CommitsPanel.initials("  Ada   Lovelace "));
        assertEquals("re", CommitsPanel.initials("renovate"));
        assertEquals("cl", CommitsPanel.initials("claude[bot]"));
        assertEquals("JF", CommitsPanel.initials("John F Kennedy"));
        assertEquals("x", CommitsPanel.initials("x"));
        assertEquals("", CommitsPanel.initials(""));
    }

    @Test
    void theListScrollsAlongWithTheSelection() throws Exception {
        List<Commit> log = java.util.stream.IntStream.range(0, 80).mapToObj(i -> commit("s" + i)).toList();
        Model model = TestModels.loaded(new SampleData()).withCommits(new Loadable.Loaded<>(log));
        CommitsPanel panel = new CommitsPanel();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, CommitsPanel.ID))) {
            testRunner.runner().focusManager().setFocus(CommitsPanel.ID);
            testRunner.pilot().pause();
            for (int i = 0; i < 45; i++) {
                testRunner.pilot().press(KeyCode.DOWN);
            }
            testRunner.pilot().pause();

            String screen = RenderedText.ofLarge(testRunner, () -> panel.render(model, CommitsPanel.ID));
            assertEquals(45, panel.selectedIndex());
            assertTrue(screen.contains("msg s45"), screen);
            assertFalse(screen.contains("msg s0 "), screen);

            testRunner.pilot().quit();
        }
    }
}
