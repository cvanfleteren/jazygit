package net.vanfleteren.jazygit.kernel.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.git.model.FileContent;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.diff.VersionMsg;
import net.vanfleteren.jazygit.features.changes.panes.files.FilesPanel;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.diff.Anchor;
import net.vanfleteren.jazygit.kernel.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.kernel.state.diff.DiffSide;
import net.vanfleteren.jazygit.kernel.state.diff.Scrolled;
import net.vanfleteren.jazygit.kernel.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.kernel.state.view.FilesShown;
import net.vanfleteren.jazygit.kernel.state.view.Preview;
import net.vanfleteren.jazygit.kernel.state.view.UnchangedNode;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * What the content panel shows of a node without changes among all files of the working tree (ADR 108): a file as it
 * is on disk, drawn as a version of itself, anything else as a line saying what it is.
 */
class UnchangedContentTest {

    private static final String README = "diff --git a/README.md b/README.md\n--- a/README.md\n+++ b/README.md\n"
            + "@@ -1,2 +1,2 @@\n # Title\n Some text\n";

    private final ContentPanel content = new ContentPanel(Theme.SpanStyles.NONE, () -> false);

    private static Model showing(UnchangedNode node) {
        return TestModels.loaded()
                .mapRepoView(view -> view.mapFiles(files -> files.withShown(FilesShown.ALL))
                        .mapFiles(files -> files.withUnchanged(Option.of(node))));
    }

    private static Model showing(String path, FileContent read) {
        return showing(new UnchangedNode.File(new Preview<>(new RepoPath(path), new Loadable.Loaded<>(read))));
    }

    private String screen(Model model) throws Exception {
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(model, FilesPanel.PANE))) {
            return RenderedText.ofLarge(testRunner, () -> content.render(model, FilesPanel.PANE));
        }
    }

    @Test
    void aFileShowsAsItIsOnDiskWithItsLinesNumberedOnce() throws Exception {
        String screen = screen(showing("README.md", new FileContent.Text(DiffText.of(README))));

        assertTrue(screen.contains("File"), screen);
        assertTrue(screen.contains("unchanged  README.md"), screen);
        assertTrue(screen.contains("# Title"), screen);
        assertTrue(screen.contains("Some text"), screen);
        assertFalse(screen.contains("new version"), screen);
        assertFalse(screen.contains("@@"), screen);
    }

    @Test
    void whatIsNoTextSaysWhatItIs() throws Exception {
        assertTrue(screen(showing("logo.png", new FileContent.Binary(12_800))).contains("Binary file · 13 KiB"));
        assertTrue(screen(showing("current", new FileContent.Link("v2/app"))).contains("Symbolic link to v2/app"));
        assertTrue(screen(showing("lib", new FileContent.Submodule(Option.of(new Sha("abcdef1234567")))))
                .contains("Submodule at abcdef1"));
    }

    @Test
    void aDirectorySaysHowManyFilesItHolds() throws Exception {
        assertTrue(screen(showing(new UnchangedNode.Directory(new RepoPath("docs/guide"), 214)))
                .contains("docs/guide · 214 files"));
        assertTrue(screen(showing(new UnchangedNode.Directory(new RepoPath(""), 3)))
                .contains("/ · 3 files"));
    }

    @Test
    void ctrlODoesNothingAndCtrlTRendersItWithoutReadingItAgain() {
        Model shown = showing("README.md", new FileContent.Text(DiffText.of(README)));
        ScrolledContent.Unchanged file = new ScrolledContent.Unchanged(new RepoPath("README.md"), DiffText.of(README));
        AreaId area = UnchangedSource.AREA.id();
        Anchor line = new Anchor.OnLine(new RepoPath("README.md"), DiffSide.NEW, 1);
        DiffCursorAt at = new DiffCursorAt(
                area,
                new Scrolled(file, 0, 0, Option.of(line), Option.none()),
                line,
                Option.none(),
                Option.none(),
                Option.none());

        assertEquals(shown, Update.update(shown, new VersionMsg.Cycled(at)).model());

        Update.Next rendered = Update.update(shown, new VersionMsg.RenderToggled(at));
        assertTrue(rendered.cmds().isEmpty());
        assertTrue(rendered.model().repoView().versions().get(area).exists(version -> version.rendered()));
    }

    @Test
    void fromTheFilesPaneCtrlTRendersItWithoutReadingItAgainAndCtrlODoesNothing() throws Exception {
        Model shown = showing("README.md", new FileContent.Text(DiffText.of(README)));

        assertEquals(shown, Update.update(shown, new VersionMsg.FilesCycled()).model());

        Update.Next rendered = Update.update(shown, new VersionMsg.FilesRenderToggled());
        assertTrue(rendered.cmds().isEmpty());
        assertTrue(rendered.model()
                .repoView()
                .versions()
                .get(UnchangedSource.AREA.id())
                .exists(version -> version.rendered()));
        // Kept with what the area shows, so drawn: the title says it is rendered.
        String screen = screen(rendered.model());
        assertTrue(screen.contains("File · rendered"), screen);
    }

    @Test
    void fromTheFilesPaneADirectoryOrWhatIsNoTextShowsNoVersion() {
        List.of(
                        showing(new UnchangedNode.Directory(new RepoPath("docs"), 3)),
                        showing("logo.png", new FileContent.Binary(12_800)))
                .forEach(shown -> {
                    Update.Next cycled = Update.update(shown, new VersionMsg.FilesCycled());
                    Update.Next rendered = Update.update(shown, new VersionMsg.FilesRenderToggled());
                    assertEquals(shown, cycled.model());
                    assertEquals(shown, rendered.model());
                    assertTrue(cycled.cmds().isEmpty());
                    assertTrue(rendered.cmds().isEmpty());
                });
    }

    @Test
    void sizesAreSaidInBytesKibOrMib() {
        assertEquals("512 B", UnchangedSource.size(512));
        assertEquals("2 KiB", UnchangedSource.size(2048));
        assertEquals("1.5 MiB", UnchangedSource.size(1024 * 1024 * 3 / 2));
    }
}
