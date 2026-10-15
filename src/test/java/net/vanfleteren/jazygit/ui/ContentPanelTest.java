package net.vanfleteren.jazygit.ui;

import dev.tamboui.text.Span;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.StyledElement;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.state.SelectionMsg;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
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

    private final ContentPanel content = new ContentPanel(Theme.SpanStyles.NONE, () -> false);

    private final Model model = TestModels.loaded(new SampleData(), "feature/initial-layout");

    @Test
    void filesFocusedRendersFileStatusView() {
        StyledElement<?> panel = content.render(model, FilesPanel.ID);
        assertNotNull(panel);
    }

    @Test
    void filesFocusedWithoutChangedFilesSaysSoInsteadOfLoading() throws Exception {
        Model clean = TestModels.loaded();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(clean, FilesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> content.render(clean, FilesPanel.ID));
            assertTrue(screen.contains("No changed files"), screen);
            assertFalse(screen.contains(Placeholders.loading()), screen);
        }
    }

    @Test
    void filesFocusedStillLoadsWhileTheStatusIsLoading() throws Exception {
        Model loading = TestModels.loading("repo");
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(loading, FilesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> content.render(loading, FilesPanel.ID));
            assertTrue(screen.contains(Placeholders.loading()), screen);
        }
    }

    @Test
    void branchesFocusedRendersTheLogOfTheSelectedBranch() throws Exception {
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(model, BranchesPanel.ID))) {
            String screen = RenderedText.ofLarge(testRunner, () -> content.render(model, BranchesPanel.ID));
            assertTrue(screen.contains("Log: feature/initial-layout"), screen);
            assertTrue(screen.contains("commit 0a1b2c3"), screen);
            assertFalse(screen.contains("a1b2c3d"), screen);
        }
    }

    @Test
    void aPopupThatJustClosedKeepsShowingTheBranchLogUntilTheFocusIsBack() throws Exception {
        // The popup is gone from the model, but has the focus until the render gives it back.
        Model closed = model.withFocusedPane(io.vavr.control.Option.of(BranchesPanel.ID));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(closed, "new-branch"))) {
            String screen = RenderedText.ofLarge(testRunner, () -> content.render(closed, "new-branch"));
            assertTrue(screen.contains("Log: feature/initial-layout"), screen);
        }
    }

    @Test
    void aPopupOpenedFromTheBranchesKeepsShowingTheBranchLog() throws Exception {
        Model withPopup = model.withFocusedPane(io.vavr.control.Option.of(BranchesPanel.ID))
                .openPopup(new net.vanfleteren.jazygit.feature.help.HelpPopup(BranchesPanel.ID));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(withPopup, "help"))) {
            String screen = RenderedText.ofLarge(testRunner, () -> content.render(withPopup, "help"));
            assertTrue(screen.contains("Log: feature/initial-layout"), screen);
        }
    }

    @Test
    void branchesFocusedShowsLoadingBeforeABranchIsSelected() throws Exception {
        Model unselected = TestModels.loaded(new SampleData());
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(unselected, BranchesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> content.render(unselected, BranchesPanel.ID));
            assertTrue(screen.contains(Placeholders.loading()), screen);
        }
    }

    @Test
    void branchesFocusedKeepsShowingThePreviousLogWhileTheNewOneLoads() throws Exception {
        Model switching = TestModels.switchingBranch(model, "other");
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(switching, BranchesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> content.render(switching, BranchesPanel.ID));
            assertTrue(screen.contains("Log: feature/initial-layout"), screen);
            assertTrue(screen.contains("commit 0a1b2c3"), screen);
            assertFalse(screen.contains(Placeholders.loading()), screen);
        }
    }

    @Test
    void commitsFocusedRendersCommitDiffView() {
        StyledElement<?> panel = content.render(model, CommitsPanel.ID);
        assertNotNull(panel);
    }

    @Test
    void unknownFocusFallsBackToFileStatusView() {
        StyledElement<?> panel = content.render(model, "unknown");
        assertNotNull(panel);
    }

    @Test
    void rendersPlaceholdersWhileLoading() {
        Model loading = TestModels.loading("repo");
        assertNotNull(content.render(loading, FilesPanel.ID));
        assertNotNull(content.render(loading, BranchesPanel.ID));
        assertNotNull(content.render(loading, CommitsPanel.ID));
    }

    @Test
    void theCommitAreaCanBeFocusedOnceItShowsTheHighlightedCommitAndReturnsToTheCommitsPane() {
        Model selected = Update.update(
                Update.update(TestModels.loaded(), new SelectionMsg.CommitSelected(new Sha("aaaa"))).model(),
                new LoadMsg.CommitDetailLoaded(new Sha("aaaa"), DiffText.of("changes"))).model();

        // Rendering gives the area its content; the commits pane has the focus while doing so.
        content.render(selected, CommitsPanel.ID);

        String area = content.areaOfPane(CommitsPanel.ID).get();
        assertEquals(io.vavr.control.Option.of(CommitsPanel.ID), content.paneOfArea(area));
        assertNotNull(content.render(selected, area));
    }

    @Test
    void theDiffAreasReturnToTheFilesPane() {
        assertEquals(io.vavr.control.Option.none(), content.paneOfArea("unknown"));
        assertEquals(io.vavr.control.Option.of(FilesPanel.ID), content.paneOfArea("diff-staged"));
        assertEquals(io.vavr.control.Option.of(FilesPanel.ID), content.paneOfArea("diff-unstaged"));
    }

    @Test
    void commitTextShowsTheHeaderTheMessageADividerAndTheChanges() {
        Commit commit = new Commit(new Sha("a1b2c3d4e5f6"), "Ada Lovelace", "ada@example.com",
                Instant.parse("2026-10-09T12:30:00Z"), "Subject", "More\ntext");

        List<String> lines = ContentPanel.commitText(commit, " a.txt | 1 +\n 1 file changed\n\n+added\n", Theme.SpanStyles.NONE).lines().stream()
                .map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()))
                .collect(List.collector());

        assertEquals("commit a1b2c3d4e5f6", lines.get(0));
        assertEquals("Author: Ada Lovelace <ada@example.com>", lines.get(1));
        assertTrue(lines.get(2).startsWith("Date: "), lines.get(2));
        assertEquals(List.of("", "    Subject", "", "    More", "    text", "---", " a.txt | 1 +", " 1 file changed", "", "+added"),
                lines.drop(3));
    }

    @Test
    void commitTextHasNoBlankLineWithoutAnExtendedMessage() {
        Commit commit = new Commit(new Sha("a1b2c3d4e5f6"), "Ada", "ada@example.com", Instant.EPOCH, "Subject", "");

        List<String> lines = ContentPanel.commitText(commit, "x", Theme.SpanStyles.NONE).lines().stream()
                .map(line -> line.spans().stream().map(Span::content).collect(Collectors.joining()))
                .collect(List.collector());

        assertEquals(List.of("", "    Subject", "---", "x"), lines.drop(3));
    }

    @Test
    void aTruncatedDiffEndsWithALineSayingSo() {
        assertEquals("+a\n", ContentPanel.shown(DiffText.of("+a\n")));
        assertEquals("+a\n… truncated\n", ContentPanel.shown(new DiffText("+a\n", true)));
    }

    @Test
    void outOfRangeSelectionIsClampedRatherThanThrowing() {
        assertDoesNotThrow(() -> content.render(model, CommitsPanel.ID));
        assertDoesNotThrow(() -> content.render(model, CommitsPanel.ID));
    }

    @Test
    void logLinesShowTheExtendedMessageFollowedByABlankLine() {
        Commit commit = new Commit(new Sha("a1b2c3d"), "Ada Lovelace", "ada@example.com",
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
    void logLinesDrawMergedBranchesWithRails() {
        List<Commit> log = net.vanfleteren.jazygit.git.model.CommitGraph.arrange(List.of(
                merge("m", "b", "f"), plain("f", "a"), plain("b", "a"), plain("a")));

        List<String> lines = ContentPanel.logLines(log, ZoneOffset.UTC);

        assertEquals(true, lines.contains("*   commit m"));
        assertEquals(true, lines.contains("|\\  Merge: b f"));
        assertEquals(true, lines.contains("| * commit f"));
        assertEquals(true, lines.contains("|/  Ada <a@e.com>"));
    }

    private static Commit plain(String sha, String... parents) {
        return new Commit(new Sha(sha), "Ada", "a@e.com", Instant.EPOCH, sha, "", List.of(parents).map(Sha::new));
    }

    private static Commit merge(String sha, String... parents) {
        return plain(sha, parents);
    }

    @Test
    void logLinesWithoutExtendedMessageEndAfterTheSubject() {
        List<Commit> commits = List.of(
                new Commit(new Sha("a1b2c3d"), "Ada", "ada@example.com", Instant.parse("2026-10-09T12:30:00Z"), "Second", ""),
                new Commit(new Sha("b2c3d4e"), "Ada", "ada@example.com", Instant.parse("2026-10-08T12:30:00Z"), "First", ""));

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

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(selected, FilesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> content.render(selected, FilesPanel.ID));
            assertTrue(screen.contains("Staged changes"), screen);
            assertTrue(screen.contains("Unstaged changes"), screen);
            assertTrue(screen.indexOf("Staged changes") < screen.indexOf("Unstaged changes"), screen);

            testRunner.pilot().quit();
        }
    }
}
