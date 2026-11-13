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
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.settings.DiffStyle;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.operation.Notice;
import net.vanfleteren.jazygit.state.view.Preview;
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
