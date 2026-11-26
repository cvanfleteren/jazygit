package net.vanfleteren.jazygit.features.changes.panes.files;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Modifier;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.RenderContext;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.vanfleteren.jazygit.core.git.model.ChangeType;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.ui.Fills;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import org.junit.jupiter.api.Test;

/**
 * What the features fill in the files pane, which knows none of them (ADR 172): the marks after a file of the working
 * tree, and what its title says after what it is.
 */
class FilesPaneSlotsTest {

    private static final Rect AREA = new Rect(0, 0, 60, 10);
    private static final FileEntry A = new FileEntry(new RepoPath("a.txt"), ChangeType.MODIFIED, true, false);
    private static final FileEntry B = new FileEntry(new RepoPath("b.txt"), ChangeType.MODIFIED, true, false);

    private static final Model MODEL = TestModels.loaded()
            .mapRepo(repo -> repo.withStatus(repo.status().map(status -> status.withFiles(List.of(A, B)))));

    // Marks a.txt, and only it.
    private static final FileMark ON_A = file -> Option.when(
            file.path().equals(new RepoPath("a.txt")),
            () -> new FileMark.Mark(List.of(LoadableList.Seg.of(" ★")), false));
    private static final FileMark ON_A_TOO = file -> Option.when(
            file.path().equals(new RepoPath("a.txt")),
            () -> new FileMark.Mark(List.of(LoadableList.Seg.of(" ☆")), false));

    @Test
    void theMarksOfAllFeaturesFollowTheNameOfTheFileTheyMarkInTheOrderOfTheFeatures() throws Exception {
        Fills fills = Fills.NONE
                .with(FilesPane.MARK, model -> Option.of(ON_A))
                .with(FilesPane.MARK, model -> Option.of(ON_A_TOO));

        String screen = render(fills);

        assertTrue(screen.contains("a.txt ★ ☆"), screen);
        assertFalse(screen.contains("b.txt ★"), screen);
    }

    @Test
    void oneMarkThatDimsDimsTheWholeRowTheMarksOfOtherFeaturesToo() throws Exception {
        FileMark dimming = file -> Option.when(
                file.path().equals(new RepoPath("a.txt")),
                () -> new FileMark.Mark(List.of(LoadableList.Seg.of(" ★")), true));
        Fills fills = Fills.NONE
                .with(FilesPane.MARK, model -> Option.of(dimming))
                .with(FilesPane.MARK, model -> Option.of(ON_A_TOO));

        Buffer buffer = buffer(fills);

        assertTrue(dims(buffer, "a.txt"), "the name");
        assertTrue(dims(buffer, "★"), "the mark that dims");
        assertTrue(dims(buffer, "☆"), "the mark of the other feature");
        assertFalse(dims(buffer, "b.txt"), "a file no mark dims");
    }

    @Test
    void theTitleSaysWhatEachFeatureGivesForItAfterWhatThePaneIs() throws Exception {
        Fills fills = Fills.NONE
                .with(FilesPane.TITLE, model -> Option.of(" · one"))
                .with(FilesPane.TITLE, model -> List.of(" · two"));

        assertTrue(render(fills).contains("Files · one · two"));
    }

    @Test
    void withNothingFilledTheFilesShowAsGitSaysThem() throws Exception {
        String screen = render(Fills.NONE);

        assertTrue(screen.contains("Files"), screen);
        assertFalse(screen.contains("★"), screen);
    }

    /**
     * Whether every cell of the first place {@code text} is drawn at is dimmed.
     */
    private static boolean dims(Buffer buffer, String text) {
        Rect area = buffer.area();
        for (int y = area.y(); y < area.bottom(); y++) {
            int row = y;
            String line = List.range(area.x(), area.right())
                    .map(x -> buffer.get(x, row).symbol())
                    .mkString();
            int at = line.indexOf(text);
            if (at >= 0) {
                return List.range(at, at + text.length())
                        .forAll(x -> buffer.get(x, row).style().addModifiers().contains(Modifier.DIM));
            }
        }
        throw new AssertionError(text + " is not drawn");
    }

    private static Buffer buffer(Fills fills) throws Exception {
        FilesPanel panel = new FilesPanel(msg -> {}, () -> {}, Theme.Highlights.DEFAULT, fills);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(MODEL, FilesPanel.PANE))) {
            CompletableFuture<Buffer> drawn = new CompletableFuture<>();
            testRunner.runner().runOnRenderThread(() -> {
                Buffer buffer = Buffer.empty(AREA);
                panel.render(MODEL, FilesPanel.PANE).render(Frame.forTesting(buffer), AREA, RenderContext.empty());
                drawn.complete(buffer);
            });
            Buffer buffer = drawn.get(5, TimeUnit.SECONDS);
            SettledPilot.of(testRunner).quit();
            return buffer;
        }
    }

    private static String render(Fills fills) throws Exception {
        FilesPanel panel = new FilesPanel(msg -> {}, () -> {}, Theme.Highlights.DEFAULT, fills);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(MODEL, FilesPanel.PANE))) {
            String screen = RenderedText.ofLarge(testRunner, () -> panel.render(MODEL, FilesPanel.PANE));
            SettledPilot.of(testRunner).quit();
            return screen;
        }
    }
}
