package net.vanfleteren.jazygit.ui.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.diff.VersionMsg;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.FileContent;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.diff.Changes;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.state.diff.WholeFile;
import net.vanfleteren.jazygit.state.view.CommitFilesView;
import net.vanfleteren.jazygit.state.view.Preview;
import net.vanfleteren.jazygit.state.view.UnchangedNode;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.files.FilesPanel;
import net.vanfleteren.jazygit.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * What the content panel shows of a node the commit whose files are listed did not change, among all its files
 * (ADR 128): a file as it is in the commit, drawn as a version of itself, a directory as how many files it holds.
 */
class CommitUnchangedContentTest {

    private static final Sha TIP_SHA = new Sha("abcdef1234567");
    private static final Changes TIP = new Changes.OfTip(new BranchName("main"), TIP_SHA);
    private static final String README = "diff --git a/README.md b/README.md\n--- a/README.md\n+++ b/README.md\n"
            + "@@ -1,2 +1,2 @@\n # Title\n Some text\n";

    private final ContentPanel content = new ContentPanel(Theme.SpanStyles.NONE, () -> false);

    private static Model showing(UnchangedNode node) {
        return TestModels.loaded()
                .mapRepoView(view -> view.mapFiles(files -> files.withCommitFiles(Option.of(CommitFilesView.of(
                                TIP, Scopes.BRANCHES.pane(), Scopes.FILES.pane())
                        .withFiles(new Loadable.Loaded<>(List.of(new CommitFile("a.txt", ChangeType.MODIFIED))))
                        .withAllFiles(true)
                        .withTree(Option.of(new Loadable.Loaded<>(List.of(
                                new CommitFile("README.md", ChangeType.UNCHANGED),
                                new CommitFile("a.txt", ChangeType.MODIFIED)))))
                        .withUnchanged(Option.of(node))))));
    }

    private static Model readme() {
        return showing(new UnchangedNode.File(
                new Preview<>("README.md", new Loadable.Loaded<>(new FileContent.Text(DiffText.of(README))))));
    }

    private String screen(Model model) throws Exception {
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(model, FilesPanel.ID))) {
            return RenderedText.ofLarge(testRunner, () -> content.render(model, FilesPanel.ID));
        }
    }

    @Test
    void aFileShowsAsItIsInTheCommitTitledWithIt() throws Exception {
        String screen = screen(readme());

        assertTrue(screen.contains("main · abcdef1 · README.md"), screen);
        assertTrue(screen.contains("# Title"), screen);
        assertTrue(screen.contains("Some text"), screen);
        assertFalse(screen.contains("@@"), screen);
    }

    @Test
    void aDirectorySaysHowManyFilesItHolds() throws Exception {
        assertTrue(screen(showing(new UnchangedNode.Directory("docs", 3))).contains("docs · 3 files"));
    }

    @Test
    void ctrlTRendersItWithoutReadingItAgainFromTheCommitItIsIn() {
        Update.Next rendered = Update.update(readme(), new VersionMsg.FilesRenderToggled());

        assertTrue(rendered.cmds().isEmpty());
        assertTrue(rendered.model()
                .repoView()
                .versions()
                .get(ContentArea.COMMIT_FILE.id())
                .exists(version -> version.rendered()));
    }

    @Test
    void itIsReadWholeFromItsCommit() {
        assertEquals(
                new WholeFile.Source.AtCommit(TIP_SHA, "README.md"),
                new ScrolledContent.Unchanged("README.md", DiffText.of(README), Option.of(TIP_SHA)).source());
        assertEquals(
                new WholeFile.Source.OnDisk("README.md"),
                new ScrolledContent.Unchanged("README.md", DiffText.of(README)).source());
    }
}
