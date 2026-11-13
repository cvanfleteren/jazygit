package net.vanfleteren.jazygit;

import static net.vanfleteren.jazygit.TestApp.SIZE;
import static net.vanfleteren.jazygit.TestApp.awaitModel;
import static net.vanfleteren.jazygit.TestApp.focusedId;
import static net.vanfleteren.jazygit.TestApp.onRenderThread;
import static net.vanfleteren.jazygit.TestApp.tickless;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.pilot.Pilot;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.StubDiffs;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.StubStatus;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.FileContent;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.Head;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.settings.DiffStyle;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.operation.Notice;
import net.vanfleteren.jazygit.state.view.CommitFilesView;
import net.vanfleteren.jazygit.state.view.Preview;
import net.vanfleteren.jazygit.state.view.UnchangedNode;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.commits.CommitsPanel;
import net.vanfleteren.jazygit.ui.content.ContentPanel;
import net.vanfleteren.jazygit.ui.files.FilesPanel;
import org.junit.jupiter.api.Test;

/**
 * J and K in the content panel move the highlight of the files pane to the next and the previous file, and the content
 * keeps the focus (ADR 117).
 */
class JazygitAppFileStepTest {

    private static final String DIR = "src/main/java/net/vanfleteren/jazygit/";
    private static final String SAMPLE_DATA = DIR + "model/SampleData.java";
    private static final String CONTENT_PANEL = DIR + "ui/ContentPanel.java";
    private static final String JAZYGIT_APP = DIR + "JazygitApp.java";

    @Test
    void jAndKMoveToTheNextAndThePreviousFileAndTheDiffKeepsTheFocus() throws Exception {
        TestApp app = new TestApp();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, SIZE, tickless())) {
            app.start(testRunner);
            Pilot pilot = inTheDiffOf(testRunner, app, SAMPLE_DATA);

            // From the directory of the first file, to that file, and past the directory of the next one to it.
            pilot.press('J');
            pilot.press('J');
            awaitModel(testRunner, app, model -> shownFiles(model).contains(List.of(CONTENT_PANEL)));
            assertInContent(testRunner);

            // Staged before, unstaged now: the focus goes with the diff to the area that shows it.
            pilot.press('J');
            awaitModel(testRunner, app, model -> shownFiles(model).contains(List.of(JAZYGIT_APP)));
            assertInContent(testRunner);

            pilot.press('K');
            awaitModel(testRunner, app, model -> shownFiles(model).contains(List.of(CONTENT_PANEL)));
            assertInContent(testRunner);
        } finally {
            app.onStop();
        }
    }

    @Test
    void inTheRichStyleJMovesToTheNextFileToo() throws Exception {
        TestApp app = new TestApp(
                new Env(SampleData.git(), StubGitHub.of()),
                new SettingsStore.Loaded(Settings.DEFAULT.withDiffStyle(DiffStyle.RICH), Option.none()));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, SIZE, tickless())) {
            app.start(testRunner);
            Pilot pilot = inTheDiffOf(testRunner, app, SAMPLE_DATA);

            pilot.press('J');
            pilot.press('J');

            awaitModel(testRunner, app, model -> shownFiles(model).contains(List.of(CONTENT_PANEL)));
            assertInContent(testRunner);
        } finally {
            app.onStop();
        }
    }

    @Test
    void kOnTheFirstFileSaysThereIsNoPreviousOne() throws Exception {
        TestApp app = new TestApp();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, SIZE, tickless())) {
            app.start(testRunner);
            Pilot pilot = inTheDiffOf(testRunner, app, SAMPLE_DATA);

            pilot.press('K');

            awaitModel(testRunner, app, model -> model.outcome().notice().contains(new Notice.NoPreviousFile()));
            assertEquals(Option.of(List.of(SAMPLE_DATA)), onRenderThread(testRunner, () -> shownFiles(app.model())));
            assertInContent(testRunner);
        } finally {
            app.onStop();
        }
    }

    @Test
    void jOnTheContentOfAnotherPaneDoesNothing() throws Exception {
        TestApp app = new TestApp();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, SIZE, tickless())) {
            app.start(testRunner);
            SettledPilot.focus(testRunner, CommitsPanel.ID, app.drawnAt::size);
            awaitModel(testRunner, app, model -> model.repoView().commitDetail().isDefined());
            Pilot pilot = SettledPilot.of(testRunner, app.drawnAt::size);
            pilot.press(KeyCode.RIGHT);
            pilot.pause();
            String area = onRenderThread(testRunner, () -> focusedId(testRunner));
            assertTrue(ContentPanel.isArea(area), area);
            Option<List<String>> files = onRenderThread(testRunner, () -> shownFiles(app.model()));

            pilot.press('J');
            pilot.pause();

            assertEquals(area, onRenderThread(testRunner, () -> focusedId(testRunner)));
            assertEquals(files, onRenderThread(testRunner, () -> shownFiles(app.model())));
            assertEquals(
                    Option.none(),
                    onRenderThread(testRunner, () -> app.model().outcome().notice()));
        } finally {
            app.onStop();
        }
    }

    @Test
    void jOnTheLastFileSaysThereIsNoNextOne() throws Exception {
        TestApp app = new TestApp();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, SIZE, tickless())) {
            app.start(testRunner);
            Pilot pilot = inTheDiffOf(testRunner, app, SAMPLE_DATA);

            // The sample has six files: walked from the directory of the first, the seventh J finds none.
            for (int step = 0; step < 6; step++) {
                pilot.press('J');
                pilot.pause();
            }
            awaitModel(testRunner, app, model -> shownFiles(model).contains(List.of("pom.xml")));
            assertEquals(
                    Option.none(),
                    onRenderThread(testRunner, () -> app.model().outcome().notice()));

            pilot.press('J');

            awaitModel(testRunner, app, model -> model.outcome().notice().contains(new Notice.NoNextFile()));
            assertEquals(Option.of(List.of("pom.xml")), onRenderThread(testRunner, () -> shownFiles(app.model())));
            assertInContent(testRunner);
        } finally {
            app.onStop();
        }
    }

    @Test
    void amongAllFilesTheWalkGoesOnThroughABinaryFileAndTheContentOfAFileWithoutChanges() throws Exception {
        TestApp app = new TestApp(new Env(allFiles(), StubGitHub.of()));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, SIZE, tickless())) {
            app.start(testRunner);
            SettledPilot.focus(testRunner, FilesPanel.ID, app.drawnAt::size);
            SettledPilot.of(testRunner, app.drawnAt::size).press('a');
            awaitModel(testRunner, app, model -> model.repoView().allFiles());
            Pilot pilot = inTheDiffOf(testRunner, app, "a.txt");
            assertFocused(testRunner, AreaId.UNSTAGED);

            // From the root, which shows the diff of a.txt alone, to a.txt itself.
            pilot.press('J');
            pilot.pause();
            assertFocused(testRunner, AreaId.UNSTAGED);

            // A binary file shows a note, which holds the focus, so the next J still goes on.
            pilot.press('J');
            awaitModel(testRunner, app, model -> unchangedShown(model).contains("b.bin"));
            pilot.pause();
            assertFocused(testRunner, AreaId.FILE_NOTE);

            pilot.press('J');
            awaitModel(testRunner, app, model -> unchangedShown(model).contains("c.txt"));
            pilot.pause();
            assertFocused(testRunner, AreaId.FILE_CONTENT);

            pilot.press('K');
            awaitModel(testRunner, app, model -> unchangedShown(model).contains("b.bin"));
            pilot.pause();
            assertFocused(testRunner, AreaId.FILE_NOTE);

            pilot.press('K');
            awaitModel(
                    testRunner,
                    app,
                    model -> model.repoView().unchanged().isEmpty()
                            && shownFiles(model).contains(List.of("a.txt")));
            pilot.pause();
            assertFocused(testRunner, AreaId.UNSTAGED);
        } finally {
            app.onStop();
        }
    }

    @Test
    void inTheFilesOfACommitJMovesToItsNextFile() throws Exception {
        TestApp app = new TestApp(new Env(withCommitFiles(), StubGitHub.of()));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, SIZE, tickless())) {
            app.start(testRunner);
            SettledPilot.focus(testRunner, CommitsPanel.ID, app.drawnAt::size);
            awaitModel(testRunner, app, model -> model.repoView().commitDetail().isDefined());
            Pilot pilot = SettledPilot.of(testRunner, app.drawnAt::size);
            pilot.press('F');
            awaitModel(
                    testRunner,
                    app,
                    model -> model.repoView()
                            .commitFiles()
                            .exists(view -> view.files().toOption().isDefined()));
            for (int row = 0;
                    row < 5
                            && !onRenderThread(testRunner, () -> commitFileShown(app.model()))
                                    .contains("x.txt");
                    row++) {
                pilot.press(KeyCode.DOWN);
                pilot.pause();
            }
            awaitModel(testRunner, app, model -> commitFileShown(model).contains("x.txt"));
            pilot.press(KeyCode.RIGHT);
            pilot.pause();
            assertFocused(testRunner, AreaId.COMMIT_FILE);

            pilot.press('J');
            awaitModel(testRunner, app, model -> commitFileShown(model).contains("y.txt"));
            pilot.pause();
            assertFocused(testRunner, AreaId.COMMIT_FILE);

            pilot.press('J');
            awaitModel(testRunner, app, model -> model.outcome().notice().contains(new Notice.NoNextFile()));
        } finally {
            app.onStop();
        }
    }

    @Test
    void kRunFromTheHelpMovesToThePreviousFileAndTheDiffGetsTheFocusBack() throws Exception {
        TestApp app = new TestApp(
                new Env(SampleData.git(), StubGitHub.of()),
                new SettingsStore.Loaded(Settings.DEFAULT.withDiffStyle(DiffStyle.RICH), Option.none()));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, SIZE, tickless())) {
            app.start(testRunner);
            Pilot pilot = inTheDiffOf(testRunner, app, CONTENT_PANEL);
            pilot.press('?');
            awaitModel(testRunner, app, model -> model.popup().isDefined());

            // From the directory of ContentPanel.java, past it, to the file before.
            pilot.press('K');

            awaitModel(
                    testRunner,
                    app,
                    model -> model.popup().isEmpty() && shownFiles(model).contains(List.of(SAMPLE_DATA)));
            pilot.pause();
            assertInContent(testRunner);
        } finally {
            app.onStop();
        }
    }

    /**
     * A working tree with one changed file, {@code a.txt}, among two without changes: the binary {@code b.bin} and
     * {@code c.txt}.
     */
    private static Git allFiles() {
        return StubGit.of()
                .withStatus(new StubStatus() {
                    @Override
                    public Either<GitError, RepoStatus> read() {
                        return Either.right(new RepoStatus(
                                Head.onBranch(new BranchName("main"), new Sha("aaaa")),
                                List.of(new FileEntry("a.txt", ChangeType.MODIFIED, false, true))));
                    }

                    @Override
                    public Either<GitError, List<String>> allFiles() {
                        return Either.right(List.of("a.txt", "b.bin", "c.txt"));
                    }

                    @Override
                    public Either<GitError, FileContent> content(String path) {
                        return Either.right(
                                path.equals("b.bin")
                                        ? new FileContent.Binary(12)
                                        : new FileContent.Text(DiffText.of("diff --git a/" + path + " b/" + path
                                                + "\n--- a/" + path + "\n+++ b/" + path + "\n@@ -1,1 +1,1 @@\n c\n")));
                    }
                })
                .withDiffs(new StubDiffs() {
                    @Override
                    public Either<GitError, Diffs> uncommitted(List<FileEntry> files, DiffOptions options) {
                        return Either.right(new Diffs("", "diff --git a/a.txt b/a.txt\n@@ -1 +1 @@\n-old\n+new\n"));
                    }
                });
    }

    /**
     * The sample repository, whose commits each changed {@code x.txt} and {@code y.txt}.
     */
    private static Git withCommitFiles() {
        Git sample = SampleData.git();
        return sample.withDiffs(new StubDiffs() {
            @Override
            public Either<GitError, DiffText> commitChanges(Sha sha, DiffOptions options) {
                return sample.diffs().commitChanges(sha, options);
            }

            @Override
            public Either<GitError, Diffs> uncommitted(List<FileEntry> files, DiffOptions options) {
                return sample.diffs().uncommitted(files, options);
            }

            @Override
            public Either<GitError, List<CommitFile>> commitFiles(Sha sha) {
                return Either.right(List.of(
                        new CommitFile("x.txt", ChangeType.MODIFIED), new CommitFile("y.txt", ChangeType.MODIFIED)));
            }

            @Override
            public Either<GitError, DiffText> commitFileChanges(Sha sha, List<CommitFile> files, DiffOptions options) {
                return Either.right(DiffText.of(files.map(file ->
                                "diff --git a/" + file.path() + " b/" + file.path() + "\n@@ -1 +1 @@\n-old\n+new\n")
                        .mkString()));
            }
        });
    }

    private static Option<String> unchangedShown(Model model) {
        return model.repoView()
                .unchanged()
                .flatMap(node -> node instanceof UnchangedNode.File(var content)
                                && content.value() instanceof Loadable.Loaded<FileContent>
                        ? Option.of(content.key())
                        : Option.none());
    }

    private static Option<String> commitFileShown(Model model) {
        return model.repoView()
                .commitFiles()
                .flatMap(CommitFilesView::diff)
                .filter(diff -> diff.value() instanceof Loadable.Loaded<DiffText>)
                .map(Preview::key)
                .filter(files -> files.size() == 1)
                .map(files -> files.head().path());
    }

    private static void assertFocused(ToolkitTestRunner testRunner, AreaId area) throws Exception {
        assertEquals(area.value(), onRenderThread(testRunner, () -> focusedId(testRunner)));
    }

    /**
     * Highlights the first node of the files pane that shows the diff of {@code path} alone, and moves the focus into
     * that diff.
     */
    private static Pilot inTheDiffOf(ToolkitTestRunner testRunner, TestApp app, String path) throws Exception {
        SettledPilot.focus(testRunner, FilesPanel.ID, app.drawnAt::size);
        Pilot pilot = SettledPilot.of(testRunner, app.drawnAt::size);
        for (int row = 0;
                row < 10
                        && !onRenderThread(testRunner, () -> shownFiles(app.model()))
                                .contains(List.of(path));
                row++) {
            pilot.press(KeyCode.DOWN);
            pilot.pause();
        }
        awaitModel(
                testRunner,
                app,
                model -> model.repoView()
                        .fileDiff()
                        .exists(diff -> diff.value().toOption().isDefined()));
        pilot.press(KeyCode.RIGHT);
        pilot.pause();
        assertInContent(testRunner);
        return pilot;
    }

    private static void assertInContent(ToolkitTestRunner testRunner) throws Exception {
        String focused = onRenderThread(testRunner, () -> focusedId(testRunner));
        assertTrue(ContentPanel.isArea(focused), focused);
    }

    private static Option<List<String>> shownFiles(Model model) {
        return model.repoView().fileDiff().map(Preview::key).map(files -> files.map(FileEntry::path));
    }
}
