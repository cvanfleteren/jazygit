package net.vanfleteren.jazygit.kernel.ui;

import static dev.tamboui.toolkit.Toolkit.panel;
import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.css.engine.StyleEngine;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.DefaultRenderContext;
import dev.tamboui.toolkit.element.ElementRegistry;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.event.EventRouter;
import dev.tamboui.toolkit.focus.FocusManager;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.state.Focus;
import net.vanfleteren.jazygit.kernel.state.Freshness;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.PaneId;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.preferences.Screen;
import net.vanfleteren.jazygit.kernel.state.preferences.TestScreens;
import net.vanfleteren.jazygit.kernel.state.view.FileStepMsg;
import net.vanfleteren.jazygit.kernel.state.view.Followed;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.screen.FilesLeftPane;
import net.vanfleteren.jazygit.kernel.ui.screen.LeftPane;
import net.vanfleteren.jazygit.kernel.ui.screen.ScreenSpec;
import net.vanfleteren.jazygit.kernel.ui.screen.Screens;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class LeftColumnTest {

    private static final Rect AREA = new Rect(0, 0, 40, 60);

    // The panes live in groups of features (ADR 158): a pane of its id stands in for each.
    private static final FilesLeftPane FILES = filesStandIn();
    private static final LeftPane BRANCHES = standIn(Scopes.BRANCHES.pane());
    private static final LeftPane COMMITS = standIn(PaneId.COMMITS);
    private static final LeftPane STASH = standIn(PaneId.STASH);

    private final LeftColumn leftColumn = new LeftColumn(
            msg -> {},
            StyleEngine.create(),
            id -> {},
            (openDiff, highlights) -> FILES,
            highlights -> BRANCHES,
            highlights -> COMMITS,
            highlights -> STASH,
            Screens.of(List.of(spec(
                    TestScreens.OTHER,
                    (dispatch, open, highlights) -> TestScreens.OTHER.panes().map(LeftColumnTest::standIn)))));

    // The repository screen, and one a feature brings (ADR 168).
    static java.util.List<Screen> screens() {
        return java.util.List.of(Screen.REPOSITORY, TestScreens.OTHER);
    }

    // The keys that resize and maximize a pane find the focused one by its place in the panes of its screen (ADR 92).
    @ParameterizedTest
    @MethodSource("screens")
    void thePanesShowTopToBottomInTheOrderTheKeysCountThem(Screen screen) {
        ElementRegistry registry = new ElementRegistry();
        FocusManager focus = new FocusManager();
        DefaultRenderContext context = new DefaultRenderContext(focus, new EventRouter(focus, registry));
        leftColumn
                .render(TestModels.loaded().withScreen(screen), Option.none(), Option.none(), Option.none())
                .column()
                .render(Frame.forTesting(Buffer.empty(AREA)), AREA, context);

        List<String> panes = screen.panes().map(PaneId::value);
        List<Integer> tops = panes.map(id -> registry.getArea(id).y());
        assertEquals(tops.sorted(), tops, "the tops of " + panes);
        assertEquals(tops.size(), tops.distinct().size(), "the tops of " + panes);
    }

    @Test
    void thePanesAFeatureMakesShowInTheOrderItsScreenListsThemWhicheverOrderItMadeThemIn() {
        // A screen the kernel does not know, whose feature makes its panes bottom first (ADR 168).
        PaneId top = new PaneId("top");
        PaneId bottom = new PaneId("bottom");
        Screen other = new Screen(
                "other",
                List.of(top, bottom),
                Settings.leftWidth("ui.other-width"),
                Settings.leftHeights("ui.other-heights", List.of(50)));
        ElementRegistry registry = new ElementRegistry();
        FocusManager focus = new FocusManager();
        DefaultRenderContext context = new DefaultRenderContext(focus, new EventRouter(focus, registry));
        columnWith(spec(other, (dispatch, open, highlights) -> List.of(standIn(bottom), standIn(top))))
                .render(TestModels.loaded().withScreen(other), Option.none(), Option.none(), Option.none())
                .column()
                .render(Frame.forTesting(Buffer.empty(AREA)), AREA, context);

        assertTrue(registry.getArea("top").y() < registry.getArea("bottom").y());
    }

    @Test
    void aScreenWhoseFeatureMadeNoPaneShowsTheStatusAlone() {
        Screen bare = new Screen(
                "bare",
                List.of(new PaneId("missing")),
                Settings.leftWidth("ui.bare-width"),
                Settings.leftHeights("ui.bare-heights", List.empty()));

        LeftColumn.Rendered rendered = columnWith(spec(bare, (dispatch, open, highlights) -> List.empty()))
                .render(TestModels.loaded().withScreen(bare), Option.none(), Option.none(), Option.none());

        assertEquals(List.empty(), rendered.panes());
    }

    private static LeftColumn columnWith(ScreenSpec spec) {
        return new LeftColumn(
                msg -> {},
                StyleEngine.create(),
                id -> {},
                (openDiff, highlights) -> FILES,
                highlights -> BRANCHES,
                highlights -> COMMITS,
                highlights -> STASH,
                Screens.of(List.of(spec)));
    }

    private static ScreenSpec spec(Screen screen, ScreenSpec.Panes panes) {
        return new ScreenSpec(
                screen,
                "screen.repository",
                panes,
                model -> Option.none(),
                model -> Freshness.NEVER,
                "status.fetch",
                model -> List.empty());
    }

    @ParameterizedTest
    @MethodSource("screens")
    void aCollapsedColumnIsAnEmptyPanelMarkedAtItsTopRight(Screen screen) {
        Rect area = new Rect(0, 0, LeftColumn.COLLAPSED_COLUMNS, 5);
        ElementRegistry registry = new ElementRegistry();
        FocusManager focus = new FocusManager();
        DefaultRenderContext context = new DefaultRenderContext(focus, new EventRouter(focus, registry));
        Buffer buffer = Buffer.empty(area);
        LeftColumn.Rendered rendered = leftColumn.render(
                TestModels.loaded()
                        .withScreen(screen)
                        .mapPreferences(preferences -> preferences.withLeftCollapsed(screen, true)),
                Option.none(),
                Option.none(),
                Option.none());
        rendered.column().render(Frame.forTesting(buffer), area, context);

        // Square without a theme, which rounds the corners.
        assertEquals(List.of("┌»┐", "│ │", "│ │", "│ │", "└─┘"), lines(buffer, area));
        assertEquals(List.empty(), rendered.panes());
        screen.panes().map(PaneId::value).forEach(id -> assertNull(registry.getArea(id), id + " is not drawn"));
    }

    private static List<String> lines(Buffer buffer, Rect area) {
        return List.range(area.y(), area.bottom())
                .map(y -> List.range(area.x(), area.right())
                        .map(x -> buffer.get(x, y).symbol())
                        .mkString());
    }

    private static FilesLeftPane filesStandIn() {
        LeftPane pane = standIn(PaneId.FILES);
        return new FilesLeftPane() {
            @Override
            public PaneId pane() {
                return pane.pane();
            }

            @Override
            public Panel render(Model model, Option<Focus> focus, Option<PaneId> inUse) {
                return pane.render(model, focus, inUse);
            }

            @Override
            public Option<Msg> selectionChange(Model model) {
                return Option.none();
            }

            @Override
            public boolean step(Model model, FileStepMsg.Step step) {
                return false;
            }

            @Override
            public Option<Msg> followChange(Model model, Option<PaneId> inUse, Option<Followed> branches) {
                return Option.none();
            }
        };
    }

    private static LeftPane standIn(PaneId id) {
        return new LeftPane() {
            @Override
            public PaneId pane() {
                return id;
            }

            @Override
            public Panel render(Model model, Option<Focus> focus, Option<PaneId> inUse) {
                return panel().id(id.id());
            }

            @Override
            public Option<Msg> selectionChange(Model model) {
                return Option.none();
            }
        };
    }
}
