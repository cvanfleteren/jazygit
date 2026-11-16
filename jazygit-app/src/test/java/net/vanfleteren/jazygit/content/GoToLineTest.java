package net.vanfleteren.jazygit.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.layout.Size;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import net.vanfleteren.jazygit.changes.diff.ExpandMsg;
import net.vanfleteren.jazygit.changes.diff.VersionMsg;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.FileContent;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.settings.DiffStyle;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.PaneId;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.Scrolled;
import net.vanfleteren.jazygit.state.load.LoadMsg;
import net.vanfleteren.jazygit.state.load.ReadWholeFile;
import net.vanfleteren.jazygit.state.load.SelectionMsg;
import net.vanfleteren.jazygit.state.view.FilesShown;
import net.vanfleteren.jazygit.state.view.Preview;
import net.vanfleteren.jazygit.state.view.UnchangedNode;
import net.vanfleteren.jazygit.state.view.ViewMsg;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.commits.CommitsPanel;
import net.vanfleteren.jazygit.ui.files.FilesPanel;
import net.vanfleteren.jazygit.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * Tests on going to a line by its number in an area of the content panel (ADR 140): {@code :}, the digits, Enter and
 * Escape, and where the cursor or the view goes.
 */
class GoToLineTest {

    private static final String SMALL = """
            diff --git a/a.txt b/a.txt
            --- a/a.txt
            +++ b/a.txt
            @@ -1,3 +1,3 @@
             one
            -two
            +three
             four
            """;

    // A file of 30 lines, line 20 changed: lines 1 to 16 are left out above the hunk.
    private static final String CHANGED = """
            diff --git a/a.txt b/a.txt
            --- a/a.txt
            +++ b/a.txt
            @@ -17,7 +17,7 @@
             line 17
             line 18
             line 19
            -line 20
            +changed 20
             line 21
             line 22
             line 23
            """;

    @Test
    void theNumberShowsAsItIsTypedDigitsOnlyAndEnterGoesToItsLine() throws Exception {
        Driven driven = new Driven(commit(DiffStyle.RICH, SMALL));
        try (ToolkitTestRunner testRunner = driven.run()) {
            driven.type(testRunner, ":3x");
            String typing = driven.screen(testRunner);
            assertTrue(typing.contains(": 3"), typing);
            assertFalse(typing.contains("3x"), typing);
            assertFalse(driven.dispatched.stream().anyMatch(ViewMsg.ContentScrolled.class::isInstance));

            driven.press(testRunner, KeyCode.ENTER);

            assertEquals(Option.of(onNew(3)), driven.lastScrolled().cursor());
            assertFalse(driven.screen(testRunner).contains(": 3"), driven.screen(testRunner));
        }
    }

    @Test
    void escapeClosesTheLineAndGoesNowhere() throws Exception {
        Driven driven = new Driven(commit(DiffStyle.RICH, SMALL));
        try (ToolkitTestRunner testRunner = driven.run()) {
            driven.type(testRunner, ":3");
            driven.press(testRunner, KeyCode.ESCAPE);

            assertFalse(driven.screen(testRunner).contains(": 3"), driven.screen(testRunner));
            assertFalse(driven.dispatched.stream().anyMatch(ViewMsg.ContentScrolled.class::isInstance));
        }
    }

    @Test
    void aLineAMarkerLeavesOutIsShownWithTheLinesAroundItAndTheCursorGoesToIt() throws Exception {
        Driven driven = new Driven(commit(DiffStyle.RICH, CHANGED));
        try (ToolkitTestRunner testRunner = driven.run()) {
            driven.type(testRunner, ":5");
            driven.press(testRunner, KeyCode.ENTER);

            assertEquals(Option.of(onNew(5)), driven.lastScrolled().cursor());
            ExpandMsg.Asked asked = List.ofAll(driven.dispatched)
                    .filter(ExpandMsg.Asked.class::isInstance)
                    .map(ExpandMsg.Asked.class::cast)
                    .single();
            assertEquals(new Anchor.Gap("a.txt", 1, Option.some(11)), asked.at().cursor());
            assertEquals(ExpandMsg.Toward.ALL, asked.toward());

            ReadWholeFile read = driven.cmds.stream()
                    .filter(ReadWholeFile.class::isInstance)
                    .map(ReadWholeFile.class::cast)
                    .findFirst()
                    .orElseThrow();
            driven.model.set(TestModels.done(
                            TestModels.running(driven.model.get(), read),
                            read,
                            new LoadMsg.WholeFileRead(read, DiffText.of(whole())))
                    .model());
            String shown = driven.screen(testRunner);
            assertTrue(shown.contains("line 5"), shown);
            assertTrue(shown.contains("line 10"), shown);
            assertFalse(shown.contains("line 11"), shown);

            // The cursor is on line 5 once it shows: the next line is 6.
            driven.type(testRunner, "j");
            assertEquals(Option.of(onNew(6)), driven.lastScrolled().cursor());
        }
    }

    @Test
    void onTheOldSideOfTheSplitViewALineLeftOutIsTheClosestLineShownAndTheTitleSaysSo() throws Exception {
        Driven driven = new Driven(commit(DiffStyle.SPLIT, CHANGED));
        try (ToolkitTestRunner testRunner = driven.run()) {
            driven.type(testRunner, ":20");
            driven.press(testRunner, KeyCode.ENTER);
            assertEquals(Option.of(onNew(20)), driven.lastScrolled().cursor());

            driven.type(testRunner, "[:19");
            driven.press(testRunner, KeyCode.ENTER);
            assertEquals(
                    Option.of(new Anchor.OnLine("a.txt", Side.LEFT, 19)),
                    driven.lastScrolled().cursor());

            driven.type(testRunner, ":5");
            driven.press(testRunner, KeyCode.ENTER);
            assertEquals(
                    Option.of(new Anchor.OnLine("a.txt", Side.LEFT, 17)),
                    driven.lastScrolled().cursor());
            assertTrue(driven.screen(testRunner).contains("line 5 is not shown · at 17"), driven.screen(testRunner));
            assertTrue(List.ofAll(driven.dispatched)
                    .filter(ExpandMsg.class::isInstance)
                    .isEmpty());
        }
    }

    @Test
    void theOriginalShownWholeIsNumberedInTheOldFile() throws Exception {
        // Line 20 removed: line 25 of the old file is line 24 of the new one.
        String removed =
                CHANGED.replace("@@ -17,7 +17,7 @@", "@@ -17,7 +17,6 @@").replace("+changed 20\n", "");
        Driven driven = new Driven(commit(DiffStyle.RICH, removed));
        try (ToolkitTestRunner testRunner = driven.run()) {
            // Ctrl+O shows the new version once the file is read whole, again the original (ADR 102).
            driven.type(testRunner, ":18");
            driven.press(testRunner, KeyCode.ENTER);
            SettledPilot.of(testRunner).press('o', dev.tamboui.tui.event.KeyModifiers.CTRL);
            SettledPilot.of(testRunner).pause();
            net.vanfleteren.jazygit.state.load.ReadVersion read = driven.cmds.stream()
                    .filter(net.vanfleteren.jazygit.state.load.ReadVersion.class::isInstance)
                    .map(net.vanfleteren.jazygit.state.load.ReadVersion.class::cast)
                    .findFirst()
                    .orElseThrow();
            driven.model.set(TestModels.done(
                            TestModels.running(driven.model.get(), read),
                            read,
                            new LoadMsg.VersionRead(
                                    read,
                                    DiffText.of(whole().replace("@@ -1,30 +1,30 @@", "@@ -1,30 +1,29 @@")
                                            .replace("+changed 20\n", ""))))
                    .model());
            driven.screen(testRunner);
            SettledPilot.of(testRunner).press('o', dev.tamboui.tui.event.KeyModifiers.CTRL);
            SettledPilot.of(testRunner).pause();
            String original = driven.screen(testRunner);
            assertTrue(original.contains("original"), original);

            driven.type(testRunner, ":25");
            driven.press(testRunner, KeyCode.ENTER);

            // A context line is anchored by its number in the new file, and found by that in the old one.
            assertEquals(Option.of(onNew(24)), driven.lastScrolled().cursor());
            driven.type(testRunner, ":20");
            driven.press(testRunner, KeyCode.ENTER);
            assertEquals(
                    Option.of(new Anchor.OnLine("a.txt", Side.LEFT, 20)),
                    driven.lastScrolled().cursor());
        }
    }

    @Test
    void thePlainDiffHasNoLineNumbersToGoToAndOpensNoLine() throws Exception {
        Driven driven = new Driven(commit(DiffStyle.PLAIN, SMALL));
        try (ToolkitTestRunner testRunner = driven.run()) {
            driven.type(testRunner, ":");
            String screen = driven.screen(testRunner);
            assertTrue(screen.contains("no line numbers to go to here"), screen);

            // No line is open to type in: the next key is no digit of it, and lets go of what the title said.
            driven.type(testRunner, "3");
            String after = driven.screen(testRunner);
            assertFalse(after.contains(": 3"), after);
            assertFalse(after.contains("no line numbers"), after);
        }
    }

    @Test
    void markdownRenderedScrollsTheBlockDrawnFromTheLineToTheTop() throws Exception {
        String paragraphs = List.rangeClosed(1, 200)
                .map(paragraph -> " Paragraph " + paragraph + "\n \n")
                .mkString();
        String readme = "diff --git a/README.md b/README.md\n--- a/README.md\n+++ b/README.md\n@@ -1,400 +1,400 @@\n"
                + paragraphs;
        Model shown = TestModels.loaded()
                .mapRepoView(view -> view.mapFiles(files -> files.withShown(FilesShown.ALL))
                        .mapFiles(files -> files.withUnchanged(Option.of(new UnchangedNode.File(new Preview<>(
                                "README.md", new Loadable.Loaded<>(new FileContent.Text(DiffText.of(readme)))))))));
        Model rendered =
                Update.update(shown, new VersionMsg.FilesRenderToggled()).model();
        Driven driven = new Driven(rendered, FilesPanel.PANE, Option.of(ContentArea.FILE_CONTENT.id()), 100);
        try (ToolkitTestRunner testRunner = driven.run()) {
            // Paragraph 41 is drawn from line 81.
            driven.type(testRunner, ":81");
            driven.press(testRunner, KeyCode.ENTER);

            String screen = driven.screen(testRunner);
            assertEquals(0, driven.row(screen, "Paragraph 41"), screen);
        }
    }

    @Test
    void jsonRenderedPutsTheCursorOnTheRowDrawnFromTheLine() throws Exception {
        String json = "diff --git a/data.json b/data.json\n--- a/data.json\n+++ b/data.json\n@@ -1,42 +1,42 @@\n {\n"
                + List.rangeClosed(1, 40)
                        .map(key -> " \"key" + key + "\": " + key + (key < 40 ? ",\n" : "\n"))
                        .mkString()
                + " }\n";
        Model shown = TestModels.loaded()
                .mapRepoView(view -> view.mapFiles(files -> files.withShown(FilesShown.ALL))
                        .mapFiles(files -> files.withUnchanged(Option.of(new UnchangedNode.File(new Preview<>(
                                "data.json", new Loadable.Loaded<>(new FileContent.Text(DiffText.of(json)))))))));
        Model rendered =
                Update.update(shown, new VersionMsg.FilesRenderToggled()).model();
        Driven driven = new Driven(rendered, FilesPanel.PANE, Option.of(ContentArea.FILE_CONTENT.id()), 100);
        try (ToolkitTestRunner testRunner = driven.run()) {
            // key30 is on line 31.
            driven.type(testRunner, ":31");
            driven.press(testRunner, KeyCode.ENTER);

            Option<Anchor> cursor = driven.lastScrolled().cursor();
            assertTrue(
                    cursor.exists(on -> on instanceof Anchor.Node node
                            && node.line().equals(new Anchor.OnLine("data.json", Side.RIGHT, 31))),
                    cursor.toString());
        }
    }

    private static Anchor onNew(int line) {
        return new Anchor.OnLine("a.txt", Side.RIGHT, line);
    }

    /**
     * The file of {@link #CHANGED} read whole, with all of it as context.
     */
    private static String whole() {
        return "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n@@ -1,30 +1,30 @@\n"
                + List.rangeClosed(1, 30)
                        .map(line -> line == 20 ? "-line 20\n+changed 20\n" : " line " + line + "\n")
                        .mkString();
    }

    private static Model commit(DiffStyle style, String diff) {
        Model model = TestModels.loaded()
                .mapPreferences(preferences ->
                        preferences.withSettings(preferences.settings().withDiffStyle(style)));
        return Update.update(
                        Update.update(model, new SelectionMsg.CommitSelected(new Sha("aaaa")))
                                .model(),
                        new LoadMsg.CommitDetailLoaded(
                                new Sha("aaaa"), model.preferences().diffOptions(), DiffText.of(diff)))
                .model();
    }

    /**
     * A content panel showing a model that takes each message the panel sends, as the app would before its next
     * frame, keeping the commands that makes.
     */
    private static final class Driven {

        final java.util.List<Msg> dispatched = new ArrayList<>();
        final java.util.List<Cmd> cmds = new ArrayList<>();
        final AtomicReference<Model> model;
        final ContentPanel panel;
        final PaneId pane;
        final Option<AreaId> focused;
        // Wide enough for the split view (ADR 139).
        final int width;

        Driven(Model start) {
            this(start, CommitsPanel.PANE, Option.none(), 140);
        }

        Driven(Model start, PaneId pane, Option<AreaId> focused, int width) {
            this.pane = pane;
            this.focused = focused;
            this.width = width;
            model = new AtomicReference<>(start);
            panel = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, msg -> {
                dispatched.add(msg);
                Update.Next next = Update.update(model.get(), msg);
                cmds.addAll(next.cmds().toJavaList());
                model.set(next.model());
            });
        }

        ToolkitTestRunner run() throws Exception {
            ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(this::render, new Size(width, 30));
            SettledPilot.focus(testRunner, area().id());
            screen(testRunner);
            return testRunner;
        }

        AreaId area() {
            return focused.getOrElse(() -> panel.areaOfPane(model.get(), pane).get());
        }

        dev.tamboui.toolkit.element.StyledElement<?> render() {
            return panel.render(model.get(), area());
        }

        String screen(ToolkitTestRunner testRunner) throws Exception {
            return RenderedText.ofSize(testRunner, this::render, width, 30);
        }

        void type(ToolkitTestRunner testRunner, String keys) throws Exception {
            for (char key : keys.toCharArray()) {
                SettledPilot.of(testRunner).press(key);
                SettledPilot.of(testRunner).pause();
                screen(testRunner);
            }
        }

        void press(ToolkitTestRunner testRunner, KeyCode key) throws Exception {
            SettledPilot.of(testRunner).press(key);
            SettledPilot.of(testRunner).pause();
            screen(testRunner);
        }

        Scrolled lastScrolled() {
            return List.ofAll(dispatched)
                    .filter(ViewMsg.ContentScrolled.class::isInstance)
                    .map(ViewMsg.ContentScrolled.class::cast)
                    .last()
                    .to();
        }

        /**
         * The index among the lines the area shows of the first that holds {@code text}: the screen starts with the
         * border of the area.
         */
        int row(String screen, String text) {
            return List.of(screen.split("\n")).indexWhere(line -> line.contains(text)) - 1;
        }
    }
}
