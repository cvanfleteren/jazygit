package net.vanfleteren.jazygit.ui;

import dev.tamboui.text.Span;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.StyledElement;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.feature.selection.SelectionMsg;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.TestModels;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests on {@link ContentPanel}, exercising its three view branches (file status,
 * branch log, commit diff) and boundary selection indices, without needing a live TUI runner.
 */
class ContentPanelTest {

    private final Model model = TestModels.loaded(new SampleData(), "feature/initial-layout");

    @Test
    void filesFocusedRendersFileStatusView() {
        StyledElement<?> panel = ContentPanel.render(model, FilesPanel.ID, 0);
        assertNotNull(panel);
    }

    @Test
    void filesFocusedWithoutChangedFilesSaysSoInsteadOfLoading() throws Exception {
        Model clean = TestModels.loaded();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> ContentPanel.render(clean, FilesPanel.ID, 0))) {
            String screen = RenderedText.of(testRunner, () -> ContentPanel.render(clean, FilesPanel.ID, 0));
            assertTrue(screen.contains("No changed files"), screen);
            assertFalse(screen.contains(Placeholders.loading()), screen);
        }
    }

    @Test
    void filesFocusedStillLoadsWhileTheStatusIsLoading() throws Exception {
        Model loading = TestModels.loading("repo");
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> ContentPanel.render(loading, FilesPanel.ID, 0))) {
            String screen = RenderedText.of(testRunner, () -> ContentPanel.render(loading, FilesPanel.ID, 0));
            assertTrue(screen.contains(Placeholders.loading()), screen);
        }
    }

    @Test
    void branchesFocusedRendersTheLogOfTheSelectedBranch() throws Exception {
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> ContentPanel.render(model, BranchesPanel.ID, 0))) {
            String screen = RenderedText.ofLarge(testRunner, () -> ContentPanel.render(model, BranchesPanel.ID, 0));
            assertTrue(screen.contains("Log: feature/initial-layout"), screen);
            assertTrue(screen.contains("commit 0a1b2c3"), screen);
            assertFalse(screen.contains("a1b2c3d"), screen);
        }
    }

    @Test
    void branchesFocusedShowsLoadingBeforeABranchIsSelected() throws Exception {
        Model unselected = TestModels.loaded(new SampleData());
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> ContentPanel.render(unselected, BranchesPanel.ID, 0))) {
            String screen = RenderedText.of(testRunner, () -> ContentPanel.render(unselected, BranchesPanel.ID, 0));
            assertTrue(screen.contains(Placeholders.loading()), screen);
        }
    }

    @Test
    void branchesFocusedKeepsShowingThePreviousLogWhileTheNewOneLoads() throws Exception {
        Model switching = TestModels.switchingBranch(model, "other");
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> ContentPanel.render(switching, BranchesPanel.ID, 0))) {
            String screen = RenderedText.of(testRunner, () -> ContentPanel.render(switching, BranchesPanel.ID, 0));
            assertTrue(screen.contains("Log: feature/initial-layout"), screen);
            assertTrue(screen.contains("commit 0a1b2c3"), screen);
            assertFalse(screen.contains(Placeholders.loading()), screen);
        }
    }

    @Test
    void commitsFocusedRendersCommitDiffView() {
        StyledElement<?> panel = ContentPanel.render(model, CommitsPanel.ID, 2);
        assertNotNull(panel);
    }

    @Test
    void unknownFocusFallsBackToFileStatusView() {
        StyledElement<?> panel = ContentPanel.render(model, "unknown", 0);
        assertNotNull(panel);
    }

    @Test
    void rendersPlaceholdersWhileLoading() {
        Model loading = TestModels.loading("repo");
        assertNotNull(ContentPanel.render(loading, FilesPanel.ID, 0));
        assertNotNull(ContentPanel.render(loading, BranchesPanel.ID, 0));
        assertNotNull(ContentPanel.render(loading, CommitsPanel.ID, 0));
    }

    @Test
    void theCommitAreaCanBeFocusedOnceItShowsTheHighlightedCommitAndReturnsToTheCommitsPane() {
        Model selected = Update.update(
                Update.update(TestModels.loaded(), new SelectionMsg.CommitSelected("aaaa")).model(),
                new LoadMsg.CommitDetailLoaded("aaaa", "changes")).model();

        // Rendering gives the area its content; the commits pane has the focus while doing so.
        ContentPanel.render(selected, CommitsPanel.ID, 0);

        String area = ContentPanel.areaOfPane(CommitsPanel.ID).orElseThrow();
        assertEquals(java.util.Optional.of(CommitsPanel.ID), ContentPanel.paneOfArea(area));
        assertNotNull(ContentPanel.render(selected, area, 0));
    }

    @Test
    void theDiffAreasReturnToTheFilesPane() {
        assertEquals(java.util.Optional.empty(), ContentPanel.paneOfArea("unknown"));
        assertEquals(java.util.Optional.of(FilesPanel.ID), ContentPanel.paneOfArea("diff-staged"));
        assertEquals(java.util.Optional.of(FilesPanel.ID), ContentPanel.paneOfArea("diff-unstaged"));
    }

    @Test
    void commitTextShowsTheHeaderTheMessageADividerAndTheChanges() {
        Commit commit = new Commit("a1b2c3d4e5f6", "a1b2c3d", "Ada Lovelace", "ada@example.com",
                Instant.parse("2026-10-09T12:30:00Z"), "Subject", "More\ntext");

        List<String> lines = ContentPanel.commitText(commit, " a.txt | 1 +\n 1 file changed\n\n+added\n").lines().stream()
                .map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()))
                .toList();

        assertEquals("commit a1b2c3d4e5f6", lines.get(0));
        assertEquals("Author: Ada Lovelace <ada@example.com>", lines.get(1));
        assertTrue(lines.get(2).startsWith("Date: "), lines.get(2));
        assertEquals(List.of("", "    Subject", "", "    More", "    text", "---", " a.txt | 1 +", " 1 file changed", "", "+added"),
                lines.subList(3, lines.size()));
    }

    @Test
    void commitTextHasNoBlankLineWithoutAnExtendedMessage() {
        Commit commit = new Commit("a1b2c3d4e5f6", "a1b2c3d", "Ada", "ada@example.com", Instant.EPOCH, "Subject", "");

        List<String> lines = ContentPanel.commitText(commit, "x").lines().stream()
                .map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()))
                .toList();

        assertEquals(List.of("", "    Subject", "---", "x"), lines.subList(3, lines.size()));
    }

    @Test
    void outOfRangeSelectionIsClampedRatherThanThrowing() {
        assertDoesNotThrow(() -> ContentPanel.render(model, CommitsPanel.ID, -5));
        assertDoesNotThrow(() -> ContentPanel.render(model, CommitsPanel.ID, 999));
    }

    @Test
    void logLinesShowTheExtendedMessageFollowedByABlankLine() {
        Commit commit = new Commit("a1b2c3d", "a1b2c3d", "Ada Lovelace", "ada@example.com",
                Instant.parse("2026-10-09T12:30:05Z"), "Add layout", "First detail\nSecond detail");

        assertEquals(List.of(
                "* commit a1b2c3d",
                "| Ada Lovelace <ada@example.com>",
                "| 2026-10-09 14:30:05",
                "| ",
                "|     Add layout",
                "| ",
                "|     First detail",
                "|     Second detail",
                "| "), ContentPanel.logLines(List.of(commit), ZoneId.of("Europe/Brussels")));
    }

    @Test
    void logLinesWithoutExtendedMessageEndAfterTheSubject() {
        List<Commit> commits = List.of(
                new Commit("a1b2c3d", "a1b2c3d", "Ada", "ada@example.com", Instant.parse("2026-10-09T12:30:00Z"), "Second", ""),
                new Commit("b2c3d4e", "b2c3d4e", "Ada", "ada@example.com", Instant.parse("2026-10-08T12:30:00Z"), "First", ""));

        assertEquals(List.of(
                "* commit a1b2c3d", "| Ada <ada@example.com>", "| 2026-10-09 12:30:00", "| ", "|     Second", "| ",
                "* commit b2c3d4e", "| Ada <ada@example.com>", "| 2026-10-08 12:30:00", "| ", "|     First", "| "),
                ContentPanel.logLines(commits, ZoneOffset.UTC));
    }

    @Test
    void filesFocusedShowsStagedAndUnstagedChangesInSeparatePanels() throws Exception {
        SampleData data = new SampleData();
        Model selected = TestModels.withFilesSelected(data, List.of(
                new FileEntry("a.txt", ChangeType.MODIFIED, true, false),
                new FileEntry("b.txt", ChangeType.MODIFIED, false, true)));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> ContentPanel.render(selected, FilesPanel.ID, 0))) {
            String screen = RenderedText.of(testRunner, () -> ContentPanel.render(selected, FilesPanel.ID, 0));
            assertTrue(screen.contains("Staged changes"), screen);
            assertTrue(screen.contains("Unstaged changes"), screen);
            assertTrue(screen.indexOf("Staged changes") < screen.indexOf("Unstaged changes"), screen);

            testRunner.pilot().quit();
        }
    }
}
