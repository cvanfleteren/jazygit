package net.vanfleteren.jazygit.kernel.content;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyEventKind;
import dev.tamboui.tui.event.KeyModifiers;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.git.model.*;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.core.repo.*;
import net.vanfleteren.jazygit.features.changes.diff.CommitFilesMsg;
import net.vanfleteren.jazygit.features.changes.panes.commits.CommitsPanel;
import net.vanfleteren.jazygit.features.changes.panes.files.FilesPanel;
import net.vanfleteren.jazygit.kernel.state.*;
import net.vanfleteren.jazygit.kernel.state.AreaId;
import net.vanfleteren.jazygit.kernel.state.diff.*;
import net.vanfleteren.jazygit.kernel.state.load.*;
import net.vanfleteren.jazygit.kernel.state.preferences.*;
import net.vanfleteren.jazygit.kernel.state.view.*;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * How a focused area of the content panel scrolls with the keys, down and sideways, and when Left or Escape
 * leaves it for its pane.
 */
class AreaScrollTest {

    private final ContentPanel content = new ContentPanel(Theme.SpanStyles.NONE, () -> false);

    @Test
    void scrollingTellsTheModelAndTheAreaShowsWhereTheModelHasIt() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        ContentPanel scrolling = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, dispatched::add);
        String changes = List.range(0, 100).map(i -> "+line " + i).mkString("\n");
        Model selected = Update.update(
                        Update.update(TestModels.loaded(), new SelectionMsg.CommitSelected(new Sha("aaaa")))
                                .model(),
                        new LoadMsg.CommitDetailLoaded(new Sha("aaaa"), DiffOptions.DEFAULT, DiffText.of(changes)))
                .model();
        AreaId area = scrolling.areaOfPane(selected, CommitsPanel.PANE).get();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> scrolling.render(selected, area))) {
            SettledPilot.focus(testRunner, area.id());
            RenderedText.ofLarge(testRunner, () -> scrolling.render(selected, area));
            SettledPilot.of(testRunner).press(dev.tamboui.tui.event.KeyCode.DOWN);
            SettledPilot.of(testRunner).pause();
        }
        ViewMsg.ContentScrolled down = (ViewMsg.ContentScrolled) dispatched.getLast();
        assertEquals(area, down.area());
        assertEquals(1, down.to().row());

        // Where the model has it scrolled to is what shows, whatever was scrolled in the element before.
        Model scrolled = selected.withScrolled(io.vavr.collection.HashMap.of(
                area,
                new Scrolled(
                        down.to().content(), 20, 0, io.vavr.control.Option.none(), io.vavr.control.Option.none())));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> scrolling.render(scrolled, area))) {
            String screen = RenderedText.ofLarge(testRunner, () -> scrolling.render(scrolled, area));
            assertFalse(screen.contains("commit aaaa"), screen);
            assertTrue(screen.contains("+line 20"), screen);
        }
    }

    @Test
    void aFocusedAreaScrollsSidewaysLeavesOnEscapeAndLeavesOtherKeysToTheApp() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        java.util.List<KeyEvent> global = new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicInteger leaves = new java.util.concurrent.atomic.AtomicInteger();
        ContentPanel scrolling = new ContentPanel(
                Theme.SpanStyles.NONE,
                () -> {
                    leaves.incrementAndGet();
                    return true;
                },
                id -> {},
                dispatched::add);
        Model selected = ContentPanelFixtures.showingCommit("+" + "x".repeat(300));
        AreaId area = scrolling.areaOfPane(selected, CommitsPanel.PANE).get();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> scrolling.render(selected, area))) {
            testRunner.runner().eventRouter().addGlobalHandler(event -> {
                if (event instanceof KeyEvent key) {
                    global.add(key);
                }
                return EventResult.UNHANDLED;
            });
            SettledPilot.focus(testRunner, area.id());
            RenderedText.ofLarge(testRunner, () -> scrolling.render(selected, area));
            SettledPilot.of(testRunner).press('x');
            SettledPilot.of(testRunner).press(KeyCode.RIGHT);
            SettledPilot.of(testRunner).pause();
            assertEquals(0, leaves.get());
            SettledPilot.of(testRunner).press(KeyCode.ESCAPE);
            SettledPilot.of(testRunner).pause();
        }

        assertEquals(
                java.util.List.of('x'),
                List.ofAll(global).map(KeyEvent::character).toJavaList());
        assertEquals(1, leaves.get());
        ViewMsg.ContentScrolled right = (ViewMsg.ContentScrolled) dispatched.getLast();
        assertEquals(
                new Scrolled(right.to().content(), 0, 1, io.vavr.control.Option.none(), io.vavr.control.Option.none()),
                right.to());
    }

    @Test
    void theNavigationKeysScrollAFocusedArea() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        // The model takes where the area is scrolled to, as the app's does.
        java.util.concurrent.atomic.AtomicReference<Model> model =
                new java.util.concurrent.atomic.AtomicReference<>(ContentPanelFixtures.showingCommit(
                        List.range(0, 100).map(i -> "+" + "x".repeat(300)).mkString("\n")));
        ContentPanel scrolling = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, msg -> {
            dispatched.add(msg);
            model.set(Update.update(model.get(), msg).model());
        });
        AreaId area = scrolling.areaOfPane(model.get(), CommitsPanel.PANE).get();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> scrolling.render(model.get(), area))) {
            SettledPilot.focus(testRunner, area.id());
            RenderedText.ofLarge(testRunner, () -> scrolling.render(model.get(), area));
            List.of(
                            KeyCode.END,
                            KeyCode.UP,
                            KeyCode.PAGE_UP,
                            KeyCode.HOME,
                            KeyCode.PAGE_DOWN,
                            KeyCode.RIGHT,
                            KeyCode.RIGHT,
                            KeyCode.LEFT)
                    .forEach(key -> SettledPilot.of(testRunner).press(key));
            SettledPilot.of(testRunner).pause();
        }

        List<Scrolled> at = List.ofAll(dispatched).map(msg -> ((ViewMsg.ContentScrolled) msg).to());
        int bottom = at.get(0).row();
        int page = at.get(4).row();
        assertTrue(bottom > 0 && page > 1, at.toString());
        assertEquals(List.of(bottom, bottom - 1, bottom - 1 - page, 0, page, page, page, page), at.map(Scrolled::row));
        assertEquals(List.of(0, 0, 0, 0, 0, 1, 2, 1), at.map(Scrolled::column));
    }

    @Test
    void hAndLScrollAFocusedAreaSidewaysAsTheArrowsDo() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        // The model takes where the area is scrolled to, as the app's does.
        java.util.concurrent.atomic.AtomicReference<Model> model = new java.util.concurrent.atomic.AtomicReference<>(
                ContentPanelFixtures.showingCommit("+0123456789" + "x".repeat(300)));
        ContentPanel scrolling = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, msg -> {
            dispatched.add(msg);
            model.set(Update.update(model.get(), msg).model());
        });
        AreaId area = scrolling.areaOfPane(model.get(), CommitsPanel.PANE).get();

        String screen;
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> scrolling.render(model.get(), area))) {
            SettledPilot.focus(testRunner, area.id());
            RenderedText.ofLarge(testRunner, () -> scrolling.render(model.get(), area));
            List.of('l', 'l', 'h').forEach(key -> SettledPilot.of(testRunner).press(key));
            SettledPilot.of(testRunner).pause();
            screen = RenderedText.ofLarge(testRunner, () -> scrolling.render(model.get(), area));
        }

        assertEquals(
                List.of(1, 2, 1),
                List.ofAll(dispatched)
                        .map(msg -> ((ViewMsg.ContentScrolled) msg).to().column()));
        // What shows is scrolled too, not only where the model has it.
        assertTrue(screen.contains("│0123456789x"), screen);
        assertFalse(screen.contains("+0123456789"), screen);
    }

    @Test
    void shiftArrowsAndCapitalHAndLScrollAFocusedAreaAllTheWaySideways() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicInteger leaves = new java.util.concurrent.atomic.AtomicInteger();
        // The model takes where the area is scrolled to, as the app's does.
        java.util.concurrent.atomic.AtomicReference<Model> model = new java.util.concurrent.atomic.AtomicReference<>(
                ContentPanelFixtures.showingCommit("+" + "x".repeat(110) + "END"));
        ContentPanel scrolling = new ContentPanel(
                Theme.SpanStyles.NONE,
                () -> {
                    leaves.incrementAndGet();
                    return true;
                },
                id -> {},
                msg -> {
                    dispatched.add(msg);
                    model.set(Update.update(model.get(), msg).model());
                });
        AreaId area = scrolling.areaOfPane(model.get(), CommitsPanel.PANE).get();

        String end;
        String start;
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(
                () -> scrolling.render(model.get(), area), new dev.tamboui.layout.Size(100, 30))) {
            var pilot = SettledPilot.of(testRunner);
            SettledPilot.focus(testRunner, area.id());
            RenderedText.ofLarge(testRunner, () -> scrolling.render(model.get(), area));
            pilot.press(KeyCode.RIGHT, KeyModifiers.SHIFT);
            pilot.pause();
            end = RenderedText.ofLarge(testRunner, () -> scrolling.render(model.get(), area));
            pilot.press(KeyCode.LEFT, KeyModifiers.SHIFT);
            pilot.pause();
            start = RenderedText.ofLarge(testRunner, () -> scrolling.render(model.get(), area));
            // All the way left already, Shift+Left stays in the area, where Left would leave it.
            pilot.press(KeyCode.LEFT, KeyModifiers.SHIFT);
            pilot.press('L');
            pilot.press('H');
            pilot.pause();
        }

        List<Integer> columns = List.ofAll(dispatched)
                .map(msg -> ((ViewMsg.ContentScrolled) msg).to().column());
        int last = columns.head();
        assertTrue(last > 0, columns.toString());
        assertEquals(List.of(last, 0, 0, last, 0), columns);
        assertEquals(0, leaves.get());
        assertTrue(java.util.regex.Pattern.compile("xEND[│┃]").matcher(end).find(), end);
        assertTrue(start.contains("│+xxx"), start);
    }

    @Test
    void rightStopsWithTheEndOfTheWidestLineAtTheBorderOfTheArea() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        // The model takes where the area is scrolled to, as the app's does.
        java.util.concurrent.atomic.AtomicReference<Model> model = new java.util.concurrent.atomic.AtomicReference<>(
                ContentPanelFixtures.showingCommit("+" + "x".repeat(110) + "END"));
        ContentPanel scrolling = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, msg -> {
            dispatched.add(msg);
            model.set(Update.update(model.get(), msg).model());
        });
        AreaId area = scrolling.areaOfPane(model.get(), CommitsPanel.PANE).get();

        String screen;
        // The frames the keys draw are as large as the screen read at the end, which the area learns its width from.
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(
                () -> scrolling.render(model.get(), area), new dev.tamboui.layout.Size(100, 30))) {
            SettledPilot.focus(testRunner, area.id());
            RenderedText.ofLarge(testRunner, () -> scrolling.render(model.get(), area));
            List.range(0, 40).forEach(i -> SettledPilot.of(testRunner).press(KeyCode.RIGHT));
            SettledPilot.of(testRunner).pause();
            screen = RenderedText.ofLarge(testRunner, () -> scrolling.render(model.get(), area));
        }

        List<Integer> columns = List.ofAll(dispatched)
                .map(msg -> ((ViewMsg.ContentScrolled) msg).to().column());
        int last = columns.last();
        assertTrue(last > 0 && last < 40, columns.toString());
        assertEquals(List.fill(40 - columns.indexOf(last), last), columns.drop(columns.indexOf(last)));
        // No column is kept for a scrollbar: the bars are on the border.
        assertTrue(java.util.regex.Pattern.compile("xEND[│┃]").matcher(screen).find(), screen);
    }

    @Test
    void sidewaysCutsTheFirstColumnsOffALineKeepingTheStylesOfItsSpans() {
        Style red = Style.EMPTY.fg(Color.RED);
        Style blue = Style.EMPTY.fg(Color.BLUE);
        Style green = Style.EMPTY.fg(Color.GREEN);
        Line line = Line.from(Span.styled("ab", red), Span.styled("cd", blue), Span.styled("漢e", green));

        assertEquals(line, TextWindow.sideways(line, 0));
        assertEquals(
                Line.from(Span.styled("b", red), Span.styled("cd", blue), Span.styled("漢e", green)),
                TextWindow.sideways(line, 1));
        assertEquals(Line.from(Span.styled("cd", blue), Span.styled("漢e", green)), TextWindow.sideways(line, 2));
        assertEquals(Line.from(Span.styled("d", blue), Span.styled("漢e", green)), TextWindow.sideways(line, 3));
        // Through the wide character: a space for its column past the cut, in the style of its span.
        assertEquals(Line.from(Span.styled(" e", green)), TextWindow.sideways(line, 5));
        assertEquals(Line.from(Span.styled("e", green)), TextWindow.sideways(line, 6));
        assertEquals(Line.from(java.util.List.of()), TextWindow.sideways(line, 9));
    }

    @Test
    void leftLeavesADiffOnceItIsScrolledAllTheWayLeft() throws Exception {
        java.util.concurrent.atomic.AtomicInteger leaves = new java.util.concurrent.atomic.AtomicInteger();
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        // The model takes where the area is scrolled to, as the app's does.
        java.util.concurrent.atomic.AtomicReference<Model> model =
                new java.util.concurrent.atomic.AtomicReference<>(TestModels.withFilesSelected(
                        SampleData.git(),
                        List.of(new FileEntry("x".repeat(300) + ".txt", ChangeType.MODIFIED, true, false))));
        ContentPanel scrolling = new ContentPanel(
                Theme.SpanStyles.NONE,
                () -> {
                    leaves.incrementAndGet();
                    return true;
                },
                id -> {},
                msg -> {
                    dispatched.add(msg);
                    model.set(Update.update(model.get(), msg).model());
                });
        AreaId area = scrolling.areaOfPane(model.get(), FilesPanel.PANE).get();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> scrolling.render(model.get(), area))) {
            SettledPilot.focus(testRunner, area.id());
            RenderedText.ofLarge(testRunner, () -> scrolling.render(model.get(), area));
            SettledPilot.of(testRunner).press(KeyCode.RIGHT);
            SettledPilot.of(testRunner).press(KeyCode.LEFT);
            // h scrolls, but does not leave as Left does.
            SettledPilot.of(testRunner).press('h');
            SettledPilot.of(testRunner).pause();
            assertEquals(0, leaves.get());
            SettledPilot.of(testRunner).press(KeyCode.LEFT);
            SettledPilot.of(testRunner).pause();
        }

        assertEquals(1, leaves.get());
        assertEquals(
                List.of(1, 0, 0),
                List.ofAll(dispatched)
                        .map(msg -> ((ViewMsg.ContentScrolled) msg).to().column()));
    }

    @Test
    void leftHeldDownStopsAtTheStartOfTheLinesAndLeavesOnlyOnTheNextPress() throws Exception {
        java.util.concurrent.atomic.AtomicInteger leaves = new java.util.concurrent.atomic.AtomicInteger();
        ContentPanel scrolling = new ContentPanel(Theme.SpanStyles.NONE, () -> {
            leaves.incrementAndGet();
            return true;
        });
        Model selected = ContentPanelFixtures.showingCommit("+" + "x".repeat(300));
        AreaId area = scrolling.areaOfPane(selected, CommitsPanel.PANE).get();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> scrolling.render(selected, area))) {
            SettledPilot.focus(testRunner, area.id());
            RenderedText.ofLarge(testRunner, () -> scrolling.render(selected, area));
            SettledPilot.of(testRunner).press(KeyCode.RIGHT);
            SettledPilot.of(testRunner).press(KeyCode.LEFT);
            // The terminal repeats Left while it is held: past the start of the lines, it neither leaves nor
            // reaches anything else.
            heldDown(testRunner, KeyCode.LEFT);
            heldDown(testRunner, KeyCode.LEFT);
            SettledPilot.of(testRunner).pause();
            assertEquals(0, leaves.get());
            SettledPilot.of(testRunner).press(KeyCode.LEFT);
            SettledPilot.of(testRunner).pause();
        }

        assertEquals(1, leaves.get());
    }

    /**
     * Sends {@code code} as a terminal does while the key is held down, and lets the frames it asks for settle.
     */
    private static void heldDown(ToolkitTestRunner testRunner, KeyCode code) {
        testRunner.runner().tuiRunner().dispatch(KeyEvent.ofKey(code, KeyModifiers.NONE, KeyEventKind.REPEAT));
        SettledPilot.settle(testRunner);
    }

    @Test
    void leftLeavesTheAreasOfThePanesBesidesTheFilesToo() throws Exception {
        java.util.concurrent.atomic.AtomicInteger leaves = new java.util.concurrent.atomic.AtomicInteger();
        ContentPanel scrolling = new ContentPanel(Theme.SpanStyles.NONE, () -> {
            leaves.incrementAndGet();
            return true;
        });
        Model selected = ContentPanelFixtures.showingCommit("+" + "x".repeat(300));
        AreaId area = scrolling.areaOfPane(selected, CommitsPanel.PANE).get();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> scrolling.render(selected, area))) {
            SettledPilot.focus(testRunner, area.id());
            RenderedText.ofLarge(testRunner, () -> scrolling.render(selected, area));
            SettledPilot.of(testRunner).press(KeyCode.LEFT);
            SettledPilot.of(testRunner).pause();
        }

        assertEquals(1, leaves.get());
    }

    @Test
    void leftLeavesTheDiffOfAFileOfACommit() throws Exception {
        java.util.concurrent.atomic.AtomicInteger leaves = new java.util.concurrent.atomic.AtomicInteger();
        ContentPanel scrolling = new ContentPanel(Theme.SpanStyles.NONE, () -> {
            leaves.incrementAndGet();
            return true;
        });
        Changes of = new Changes.OfCommit(new Sha("aaaa"));
        CommitFile file = new CommitFile("src/a.txt", ChangeType.MODIFIED);
        Model open = Update.update(
                        Update.update(TestModels.loaded(), new CommitFilesMsg.Opened(of, Scopes.COMMITS.pane()))
                                .model(),
                        new LoadMsg.CommitFilesLoaded(of, List.of(file)))
                .model();
        Model selected = Update.update(
                        Update.update(open, new SelectionMsg.CommitFilesSelected(List.of(file)))
                                .model(),
                        new LoadMsg.CommitFileDiffLoaded(
                                of, List.of(file), DiffOptions.DEFAULT, DiffText.of("+" + "x".repeat(300))))
                .model();
        AreaId area = scrolling.areaOfPane(selected, FilesPanel.PANE).get();
        assertEquals(CommitFilesSource.AREA.id(), area);

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> scrolling.render(selected, area))) {
            SettledPilot.focus(testRunner, area.id());
            RenderedText.ofLarge(testRunner, () -> scrolling.render(selected, area));
            SettledPilot.of(testRunner).press(KeyCode.LEFT);
            SettledPilot.of(testRunner).pause();
        }

        assertEquals(1, leaves.get());
    }

    @Test
    void escapeGoesToTheAppWhenTheAreaCannotBeLeft() throws Exception {
        java.util.List<KeyEvent> global = new java.util.ArrayList<>();
        Model selected = ContentPanelFixtures.showingCommit("+added line");
        AreaId area = content.areaOfPane(selected, CommitsPanel.PANE).get();

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> content.render(selected, area))) {
            testRunner.runner().eventRouter().addGlobalHandler(event -> {
                if (event instanceof KeyEvent key) {
                    global.add(key);
                }
                return EventResult.UNHANDLED;
            });
            SettledPilot.focus(testRunner, area.id());
            RenderedText.ofLarge(testRunner, () -> content.render(selected, area));
            SettledPilot.of(testRunner).press(KeyCode.ESCAPE);
            SettledPilot.of(testRunner).pause();
        }

        assertEquals(
                java.util.List.of(KeyCode.ESCAPE),
                List.ofAll(global).map(KeyEvent::code).toJavaList());
    }

    @Test
    void theAreaScrollsOnFromWhereTheModelHasItScrolledSideways() throws Exception {
        java.util.List<Msg> dispatched = new java.util.ArrayList<>();
        ContentPanel scrolling = new ContentPanel(Theme.SpanStyles.NONE, () -> false, id -> {}, dispatched::add);
        Model selected = ContentPanelFixtures.showingCommit(
                List.range(0, 100).map(i -> "+" + "x".repeat(300)).mkString("\n"));
        AreaId area = scrolling.areaOfPane(selected, CommitsPanel.PANE).get();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> scrolling.render(selected, area))) {
            SettledPilot.focus(testRunner, area.id());
            RenderedText.ofLarge(testRunner, () -> scrolling.render(selected, area));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).pause();
        }
        ScrolledContent shown =
                ((ViewMsg.ContentScrolled) dispatched.getLast()).to().content();

        // TamboUI does not draw the columns scrolled past, so the next scroll tells where the area was.
        Model scrolled = selected.withScrolled(io.vavr.collection.HashMap.of(
                area, new Scrolled(shown, 0, 11, io.vavr.control.Option.none(), io.vavr.control.Option.none())));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> scrolling.render(scrolled, area))) {
            SettledPilot.focus(testRunner, area.id());
            RenderedText.ofLarge(testRunner, () -> scrolling.render(scrolled, area));
            SettledPilot.of(testRunner).press(KeyCode.DOWN);
            SettledPilot.of(testRunner).pause();
        }
        assertEquals(
                new Scrolled(shown, 1, 11, io.vavr.control.Option.none(), io.vavr.control.Option.none()),
                ((ViewMsg.ContentScrolled) dispatched.getLast()).to());
    }
}
