package net.vanfleteren.jazygit.ui.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.settings.DiffStyle;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.Found;
import net.vanfleteren.jazygit.state.load.LoadMsg;
import net.vanfleteren.jazygit.state.load.SelectionMsg;
import net.vanfleteren.jazygit.state.view.ContentSearchMsg;
import net.vanfleteren.jazygit.state.view.ViewMsg;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.commits.CommitsPanel;
import net.vanfleteren.jazygit.ui.files.FilesPanel;
import net.vanfleteren.jazygit.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * Tests on searching what an area of the content panel shows (ADR 126): {@code /}, Enter, {@code n}, {@code N} and
 * Escape in an area, and what its title says.
 */
class ContentSearchTest {

    private static final String DIFF = """
            diff --git a/a.txt b/a.txt
            --- a/a.txt
            +++ b/a.txt
            @@ -1,3 +1,3 @@
             one foo
            -two
            +three foo
             four Foo
            """;

    @Test
    void enterGoesToTheFirstMatchFromTheCursorAndNAndShiftNGoOnWrapping() throws Exception {
        Driven driven = new Driven(commit(DiffStyle.RICH));
        String area = driven.area();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(driven::render)) {
            SettledPilot.focus(testRunner, area);
            driven.screen(testRunner);
            driven.type(testRunner, "/foo");
            // Nothing is searched while the line is open.
            assertTrue(driven.screen(testRunner).contains("/ foo"), driven.screen(testRunner));
            assertFalse(driven.dispatched.stream().anyMatch(ViewMsg.ContentScrolled.class::isInstance));

            driven.press(testRunner, KeyCode.ENTER);
            String first = driven.screen(testRunner);
            assertTrue(first.contains("/foo 1/3"), first);
            assertEquals(
                    Option.of(new Found.InStop(onNew(1), 0)),
                    driven.lastScrolled().found());
            assertEquals(Option.of(onNew(1)), driven.lastScrolled().cursor());

            driven.type(testRunner, "n");
            assertTrue(driven.screen(testRunner).contains("/foo 2/3"), driven.screen(testRunner));
            assertEquals(Option.of(onNew(2)), driven.lastScrolled().cursor());

            driven.type(testRunner, "nn");
            String wrapped = driven.screen(testRunner);
            assertTrue(wrapped.contains("/foo 1/3 · search wrapped"), wrapped);
            assertEquals(Option.of(onNew(1)), driven.lastScrolled().cursor());

            driven.type(testRunner, "N");
            String back = driven.screen(testRunner);
            assertTrue(back.contains("/foo 3/3 · search wrapped"), back);
            assertEquals(Option.of(onNew(3)), driven.lastScrolled().cursor());

            // Any other key lets go of what the title said.
            driven.type(testRunner, "k");
            assertFalse(driven.screen(testRunner).contains("wrapped"), driven.screen(testRunner));
        }
    }

    @Test
    void anUppercaseLetterMakesTheSearchMatchCase() throws Exception {
        Driven driven = new Driven(commit(DiffStyle.RICH));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(driven::render)) {
            SettledPilot.focus(testRunner, driven.area());
            driven.screen(testRunner);
            driven.type(testRunner, "/Foo");
            driven.press(testRunner, KeyCode.ENTER);

            assertTrue(driven.screen(testRunner).contains("/Foo 1/1"), driven.screen(testRunner));
            assertEquals(Option.of(onNew(3)), driven.lastScrolled().cursor());
        }
    }

    @Test
    void escapeClearsTheSearchBeforeItLeavesTheArea() throws Exception {
        Driven driven = new Driven(commit(DiffStyle.RICH));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(driven::render)) {
            SettledPilot.focus(testRunner, driven.area());
            driven.screen(testRunner);
            driven.type(testRunner, "/foo");
            driven.press(testRunner, KeyCode.ENTER);
            driven.press(testRunner, KeyCode.ESCAPE);

            assertInstanceOf(ContentSearchMsg.Cleared.class, driven.dispatched.getLast());
            assertTrue(driven.model.get().contentSearch().isEmpty());
            assertFalse(driven.screen(testRunner).contains("/foo"), driven.screen(testRunner));
        }
    }

    @Test
    void escapeInTheLinePutsBackTheSearchFromBefore() throws Exception {
        Driven driven = new Driven(commit(DiffStyle.RICH));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(driven::render)) {
            SettledPilot.focus(testRunner, driven.area());
            driven.screen(testRunner);
            driven.type(testRunner, "/foo");
            driven.press(testRunner, KeyCode.ENTER);
            driven.type(testRunner, "/x");
            driven.press(testRunner, KeyCode.ESCAPE);

            assertEquals("foo", driven.model.get().contentSearch().get().text());
            assertTrue(driven.screen(testRunner).contains("/foo"), driven.screen(testRunner));
        }
    }

    @Test
    void thePlainDiffIsSearchedWithoutTheMarkersOfItsLinesAndSaysWhenNothingMatches() throws Exception {
        Driven driven = new Driven(commit(DiffStyle.PLAIN));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(driven::render)) {
            SettledPilot.focus(testRunner, driven.area());
            driven.screen(testRunner);
            driven.type(testRunner, "/+three");
            driven.press(testRunner, KeyCode.ENTER);
            String none = driven.screen(testRunner);
            assertTrue(none.contains("/+three · no matches"), none);

            driven.type(testRunner, "/");
            // The line opens with the query in it.
            assertTrue(driven.screen(testRunner).contains("/ +three"), driven.screen(testRunner));
            for (int typed = 0; typed < "+three".length(); typed++) {
                driven.press(testRunner, KeyCode.BACKSPACE);
            }
            driven.type(testRunner, "thre");
            driven.press(testRunner, KeyCode.ENTER);
            String found = driven.screen(testRunner);
            assertTrue(found.contains("/thre 1/1"), found);
            assertEquals(
                    Option.of(new Found.OnLine(driven.row(found, "+three foo"), 0)),
                    driven.lastScrolled().found());
        }
    }

    @Test
    void aKeptSearchCountsTheMatchesOfWhatShowsNextWithoutGoingToOne() throws Exception {
        Driven driven = new Driven(commit(DiffStyle.RICH));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(driven::render)) {
            SettledPilot.focus(testRunner, driven.area());
            driven.screen(testRunner);
            driven.type(testRunner, "/foo");
            driven.press(testRunner, KeyCode.ENTER);
            // The commit read again, as after R, now with one match less.
            Model other = Update.update(
                            driven.model.get(),
                            new LoadMsg.CommitDetailLoaded(
                                    new Sha("aaaa"),
                                    driven.model.get().preferences().diffOptions(),
                                    DiffText.of(DIFF.replace("one foo", "one bar"))))
                    .model();
            driven.model.set(other);

            String screen = driven.screen(testRunner);
            assertTrue(screen.contains("/foo · 2 matches"), screen);
        }
    }

    @Test
    void inTheDiffOfAPullRequestNGoesToTheMatchesWhileASearchIsKeptAndToTheThreadsOnceItIsCleared() throws Exception {
        Driven driven = new Driven(pullRequest(), FilesPanel.ID);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(driven::render)) {
            SettledPilot.focus(testRunner, driven.area());
            driven.screen(testRunner);
            driven.type(testRunner, "/a");
            driven.press(testRunner, KeyCode.ENTER);
            assertTrue(driven.screen(testRunner).contains("/a 1/"), driven.screen(testRunner));
            // The path on the bar of the file, then the line: the cursor goes to each.
            assertEquals(
                    Option.of(new Anchor.File("a.txt")), driven.lastScrolled().cursor());
            driven.type(testRunner, "n");
            assertEquals(Option.of(onNew(1)), driven.lastScrolled().cursor());

            driven.press(testRunner, KeyCode.ESCAPE);
            driven.type(testRunner, "n");
            assertEquals(Option.of(THREAD), driven.lastScrolled().cursor());
            assertEquals(Option.none(), driven.lastScrolled().found());
        }
    }

    @Test
    void whileARangeIsOpenAMatchOutsideItsHunkIsRefused() throws Exception {
        Driven driven = new Driven(pullRequest(), FilesPanel.ID);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(driven::render)) {
            SettledPilot.focus(testRunner, driven.area());
            driven.screen(testRunner);
            driven.type(testRunner, "jv/why");
            driven.press(testRunner, KeyCode.ENTER);

            String screen = driven.screen(testRunner);
            assertTrue(screen.contains("that match is outside the range"), screen);
            assertEquals(Option.of(onNew(1)), driven.lastScrolled().cursor());

            // Escape drops the range before it clears the search.
            driven.press(testRunner, KeyCode.ESCAPE);
            assertEquals(Option.none(), driven.lastScrolled().mark());
            assertTrue(driven.model.get().contentSearch().isDefined());
        }
    }

    @Test
    void aMatchGoneToStaysCurrentWhenTheRowsAboveItMoveAsTheThreadsAreReadAgain() throws Exception {
        Driven driven = new Driven(ContentPanelTest.pullRequestDiff("Why?", """
                        diff --git a/a.txt b/a.txt
                        --- a/a.txt
                        +++ b/a.txt
                        @@ -0,0 +1,2 @@
                        +a
                        +zed
                        """), FilesPanel.ID);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(driven::render)) {
            SettledPilot.focus(testRunner, driven.area());
            driven.screen(testRunner);
            driven.type(testRunner, "/zed");
            driven.press(testRunner, KeyCode.ENTER);
            String before = driven.screen(testRunner);
            assertTrue(before.contains("/zed 1/1"), before);

            // A reply makes the thread above the match longer.
            driven.model.set(
                    Update.update(driven.model.get(), ContentPanelTest.remarksLoaded("Why?\n\nOne.\n\nTwo.\n\nThree."))
                            .model());
            String after = driven.screen(testRunner);
            assertTrue(after.contains("Three."), after);
            assertTrue(after.contains("/zed 1/1"), after);
        }
    }

    @Test
    void whatTheTitleSaidOfTheSearchGoesWhenTheAreaLosesTheFocus() throws Exception {
        Driven driven = new Driven(commit(DiffStyle.RICH));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(driven::render)) {
            SettledPilot.focus(testRunner, driven.area());
            driven.screen(testRunner);
            driven.type(testRunner, "/Foo");
            driven.press(testRunner, KeyCode.ENTER);
            driven.type(testRunner, "n");
            assertTrue(driven.screen(testRunner).contains("search wrapped"), driven.screen(testRunner));

            String elsewhere =
                    RenderedText.ofLarge(testRunner, () -> driven.panel.render(driven.model.get(), CommitsPanel.ID));
            assertTrue(elsewhere.contains("/Foo 1/1"), elsewhere);
            assertFalse(elsewhere.contains("search wrapped"), elsewhere);
        }
    }

    @Test
    void aMatchPastTheRightEdgeIsScrolledToSideways() throws Exception {
        Driven driven =
                new Driven(commit(DiffStyle.RICH, DIFF.replace("+three foo", "+" + "x".repeat(200) + " needle")));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(driven::render)) {
            SettledPilot.focus(testRunner, driven.area());
            assertFalse(driven.screen(testRunner).contains("needle"), driven.screen(testRunner));
            driven.type(testRunner, "/needle");
            driven.press(testRunner, KeyCode.ENTER);

            assertTrue(
                    driven.lastScrolled().column() > 100,
                    String.valueOf(driven.lastScrolled().column()));
            String screen = driven.screen(testRunner);
            assertTrue(screen.contains("needle"), screen);
        }
    }

    @Test
    void withoutACursorAMatchBelowTheScreenIsScrolledToTheMiddleOfIt() throws Exception {
        String lines = List.range(0, 200).map(at -> "+line " + at).mkString("\n");
        Driven driven = new Driven(commit(
                DiffStyle.PLAIN,
                "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n@@ -0,0 +1,200 @@\n" + lines + "\n"));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(driven::render)) {
            SettledPilot.focus(testRunner, driven.area());
            driven.screen(testRunner);
            driven.type(testRunner, "/line 150");
            driven.press(testRunner, KeyCode.ENTER);

            String screen = driven.screen(testRunner);
            assertTrue(screen.contains("+line 150"), screen);
            // The line of the match is about the middle of the area, not its edge.
            int shown = driven.row(screen, "+line 150");
            assertTrue(shown > 5 && shown < 25, screen);
            Found.OnLine found = (Found.OnLine) driven.lastScrolled().found().get();
            assertEquals(0, found.nth());
            assertEquals(found.line(), driven.lastScrolled().row() + shown);
        }
    }

    @Test
    void theSecondMatchOfAStopIsFoundAgainInTheNextFrame() throws Exception {
        Driven driven = new Driven(commit(DiffStyle.RICH, DIFF.replace(" one foo", " one foo and foo")));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(driven::render)) {
            SettledPilot.focus(testRunner, driven.area());
            driven.screen(testRunner);
            driven.type(testRunner, "/foo");
            driven.press(testRunner, KeyCode.ENTER);
            driven.type(testRunner, "n");

            assertEquals(
                    Option.of(new Found.InStop(onNew(1), 1)),
                    driven.lastScrolled().found());
            assertTrue(driven.screen(testRunner).contains("/foo 2/4"), driven.screen(testRunner));
            assertTrue(driven.screen(testRunner).contains("/foo 2/4"), driven.screen(testRunner));
        }
    }

    @Test
    void theStagedAndUnstagedAreasEachCountTheirMatchesAndOnlyTheFocusedOneHasACurrentOne() throws Exception {
        Model selected = TestModels.withFilesSelected(
                net.vanfleteren.jazygit.model.SampleData.git(),
                List.of(
                        new net.vanfleteren.jazygit.git.model.FileEntry(
                                "a.txt", net.vanfleteren.jazygit.git.model.ChangeType.MODIFIED, true, false),
                        new net.vanfleteren.jazygit.git.model.FileEntry(
                                "b.txt", net.vanfleteren.jazygit.git.model.ChangeType.MODIFIED, false, true)));
        Driven driven =
                new Driven(selected, FilesPanel.ID, ContentArea.UNSTAGED.id().value());
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(driven::render)) {
            SettledPilot.focus(testRunner, driven.area());
            driven.screen(testRunner);
            driven.type(testRunner, "/new");
            driven.press(testRunner, KeyCode.ENTER);

            String screen = driven.screen(testRunner);
            assertTrue(screen.contains("Staged changes · /new · 1 match"), screen);
            assertTrue(screen.contains("Unstaged changes · /new 1/1"), screen);
        }
    }

    private static final Anchor THREAD = new Anchor.Thread(
            new net.vanfleteren.jazygit.gh.model.ReviewThread.Id("T1"),
            Option.of(new Anchor.OnLine("a.txt", net.vanfleteren.jazygit.gh.model.ReviewThread.Side.RIGHT, 1)));

    /**
     * The diff of a pull request's files, of one added line {@code a} with a thread on it that asks "Why?".
     */
    private static Model pullRequest() {
        return ContentPanelTest.pullRequestDiff("Why?", """
                diff --git a/a.txt b/a.txt
                --- a/a.txt
                +++ b/a.txt
                @@ -0,0 +1 @@
                +a
                """);
    }

    private static Anchor onNew(int line) {
        return new Anchor.OnLine("a.txt", net.vanfleteren.jazygit.gh.model.ReviewThread.Side.RIGHT, line);
    }

    private static Model commit(DiffStyle style) {
        return commit(style, DIFF);
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
     * frame.
     */
    private static final class Driven {

        final java.util.List<Msg> dispatched = new ArrayList<>();
        final AtomicReference<Model> model;
        final ContentPanel panel;

        final String pane;
        final Option<String> focused;

        Driven(Model start) {
            this(start, CommitsPanel.ID);
        }

        Driven(Model start, String pane) {
            this(start, pane, Option.none());
        }

        Driven(Model start, String pane, String focused) {
            this(start, pane, Option.of(focused));
        }

        private Driven(Model start, String pane, Option<String> focused) {
            this.pane = pane;
            this.focused = focused;
            model = new AtomicReference<>(start);
            panel = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, msg -> {
                dispatched.add(msg);
                model.set(Update.update(model.get(), msg).model());
            });
        }

        String area() {
            return focused.getOrElse(() -> panel.areaOfPane(model.get(), pane).get());
        }

        dev.tamboui.toolkit.element.StyledElement<?> render() {
            return panel.render(model.get(), area());
        }

        String screen(ToolkitTestRunner testRunner) throws Exception {
            return RenderedText.ofLarge(testRunner, this::render);
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

        net.vanfleteren.jazygit.state.diff.Scrolled lastScrolled() {
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
