package net.vanfleteren.jazygit.kernel.content;

import static net.vanfleteren.jazygit.app.GitHubKeys.github;
import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import net.vanfleteren.jazygit.core.git.model.DiffText;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.panes.commits.CommitsPanel;
import net.vanfleteren.jazygit.features.github.builds.BuildsAction;
import net.vanfleteren.jazygit.features.github.builds.BuildsMsg;
import net.vanfleteren.jazygit.features.github.builds.BuildsView;
import net.vanfleteren.jazygit.features.github.builds.content.BuildsPickTest;
import net.vanfleteren.jazygit.kernel.settings.DiffStyle;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.FeatureMsg;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.Update;
import net.vanfleteren.jazygit.kernel.state.diff.Anchor;
import net.vanfleteren.jazygit.kernel.state.diff.DiffSide;
import net.vanfleteren.jazygit.kernel.state.load.LoadMsg;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.state.view.ViewMsg;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.files.FilesPanel;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * The tests of ContentSearchTest with GitHub in the app (ADR 171).
 */
class ContentSearchGitHubTest {

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
    void inTheDiffOfAPullRequestNGoesToTheMatchesWhileASearchIsKeptAndToTheThreadsOnceItIsCleared() throws Exception {
        Driven driven = new Driven(pullRequest(), FilesPanel.PANE);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(driven::render)) {
            SettledPilot.focus(testRunner, driven.area().id());
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
        Driven driven = new Driven(pullRequest(), FilesPanel.PANE);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(driven::render)) {
            SettledPilot.focus(testRunner, driven.area().id());
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
        Driven driven = new Driven(PullRequestContentTest.pullRequestDiff("Why?", """
                        diff --git a/a.txt b/a.txt
                        --- a/a.txt
                        +++ b/a.txt
                        @@ -0,0 +1,2 @@
                        +a
                        +zed
                        """), FilesPanel.PANE);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(driven::render)) {
            SettledPilot.focus(testRunner, driven.area().id());
            driven.screen(testRunner);
            driven.type(testRunner, "/zed");
            driven.press(testRunner, KeyCode.ENTER);
            String before = driven.screen(testRunner);
            assertTrue(before.contains("/zed 1/1"), before);

            // A reply makes the thread above the match longer.
            driven.model.set(Update.update(
                            driven.model.get(), PullRequestContentTest.remarksLoaded("Why?\n\nOne.\n\nTwo.\n\nThree."))
                    .model());
            String after = driven.screen(testRunner);
            assertTrue(after.contains("Three."), after);
            assertTrue(after.contains("/zed 1/1"), after);
        }
    }

    @Test
    void inTheBuildsNAndShiftNGoToTheMatchesWhileASearchIsKeptElseToAnotherBuild() throws Exception {
        List<String> keys = net.vanfleteren.jazygit.kernel.ui.keys.Keymap.standard()
                .bindings(BuildsAction.SCOPE.id())
                .map(net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding::key);
        Driven driven = new Driven(
                BuildsPickTest.steppable(),
                net.vanfleteren.jazygit.kernel.ui.branches.BranchesPanel.PANE,
                BuildsView.AREA);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(driven::render)) {
            SettledPilot.focus(testRunner, driven.area().id());
            driven.screen(testRunner);
            driven.type(testRunner, "?");
            List<Option<Msg>> before = driven.help().actions();
            assertEquals(Option.of(github(new BuildsMsg.BuildStepped(1))), before.get(keys.indexOf("n")));
            driven.type(testRunner, "n");
            assertEquals(1, stepped(driven));

            driven.type(testRunner, "/Older");
            driven.press(testRunner, KeyCode.ENTER);
            driven.type(testRunner, "nN");
            assertEquals(1, stepped(driven));
            assertTrue(driven.screen(testRunner).contains("/Older"), driven.screen(testRunner));

            // The help says n and N do not step the builds then.
            driven.type(testRunner, "?");
            List<Option<Msg>> searching = driven.help().actions();
            assertTrue(searching.get(keys.indexOf("n")).isEmpty());
            assertTrue(searching.get(keys.indexOf("N")).isEmpty());
        }
    }

    private static long stepped(Driven driven) {
        return driven.dispatched.stream()
                .filter(BuildsMsg.BuildStepped.class::isInstance)
                .count();
    }

    private static final Anchor THREAD = new Anchor.Thread(
            new net.vanfleteren.jazygit.kernel.state.diff.RemarkThread.Id("T1"),
            Option.of(new Anchor.OnLine("a.txt", DiffSide.NEW, 1)));

    /**
     * The diff of a pull request's files, of one added line {@code a} with a thread on it that asks "Why?".
     */
    private static Model pullRequest() {
        return PullRequestContentTest.pullRequestDiff("Why?", """
                diff --git a/a.txt b/a.txt
                --- a/a.txt
                +++ b/a.txt
                @@ -0,0 +1 @@
                +a
                """);
    }

    private static Anchor onNew(int line) {
        return new Anchor.OnLine("a.txt", DiffSide.NEW, line);
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

        final PaneId pane;
        final Option<AreaId> focused;

        Driven(Model start) {
            this(start, CommitsPanel.PANE);
        }

        Driven(Model start, PaneId pane) {
            this(start, pane, Option.none());
        }

        Driven(Model start, PaneId pane, AreaId focused) {
            this(start, pane, Option.of(focused));
        }

        private Driven(Model start, PaneId pane, Option<AreaId> focused) {
            this.pane = pane;
            this.focused = focused;
            model = new AtomicReference<>(start);
            panel = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, msg -> {
                dispatched.add(msg instanceof FeatureMsg.Owned(var feature, Msg owned) ? owned : msg);
                model.set(Update.update(model.get(), msg).model());
            });
        }

        AreaId area() {
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

        /**
         * The help the area asked for last, with the actions of its keys.
         */
        net.vanfleteren.jazygit.features.chrome.help.HelpMsg.Requested help() {
            return List.ofAll(dispatched)
                    .filter(net.vanfleteren.jazygit.features.chrome.help.HelpMsg.Requested.class::isInstance)
                    .map(net.vanfleteren.jazygit.features.chrome.help.HelpMsg.Requested.class::cast)
                    .last();
        }

        net.vanfleteren.jazygit.kernel.state.diff.Scrolled lastScrolled() {
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
