package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.diff.VersionMsg;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.FileContent;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.state.diff.Scrolled;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.state.view.Preview;
import net.vanfleteren.jazygit.state.view.UnchangedNode;
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
        return TestModels.loaded().mapRepoView(view -> view.withAllFiles(true).withUnchanged(Option.of(node)));
    }

    private static Model showing(String path, FileContent read) {
        return showing(new UnchangedNode.File(new Preview<>(path, new Loadable.Loaded<>(read))));
    }

    private String screen(Model model) throws Exception {
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(model, FilesPanel.ID))) {
            return RenderedText.ofLarge(testRunner, () -> content.render(model, FilesPanel.ID));
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
        assertTrue(
                screen(showing(new UnchangedNode.Directory("docs/guide", 214))).contains("docs/guide · 214 files"));
        assertTrue(screen(showing(new UnchangedNode.Directory("", 3))).contains("/ · 3 files"));
    }

    @Test
    void ctrlODoesNothingAndCtrlTRendersItWithoutReadingItAgain() {
        Model shown = showing("README.md", new FileContent.Text(DiffText.of(README)));
        ScrolledContent.Unchanged file = new ScrolledContent.Unchanged("README.md", DiffText.of(README));
        AreaId area = ContentArea.FILE_CONTENT.id();
        Anchor line = new Anchor.OnLine("README.md", ReviewThread.Side.RIGHT, 1);
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
    void sizesAreSaidInBytesKibOrMib() {
        assertEquals("512 B", ContentView.size(512));
        assertEquals("2 KiB", ContentView.size(2048));
        assertEquals("1.5 MiB", ContentView.size(1024 * 1024 * 3 / 2));
    }
}
