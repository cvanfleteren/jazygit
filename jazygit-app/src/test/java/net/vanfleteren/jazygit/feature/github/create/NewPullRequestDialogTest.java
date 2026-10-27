package net.vanfleteren.jazygit.feature.github.create;

import static dev.tamboui.toolkit.Toolkit.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.DefaultRenderContext;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.ElementRegistry;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.event.EventRouter;
import dev.tamboui.toolkit.focus.FocusManager;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.ArrayList;
import java.util.Optional;
import net.vanfleteren.jazygit.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.ListCursor;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.widgets.Dimmed;
import org.junit.jupiter.api.Test;

/**
 * The popup asking what a new pull request merges into and what it says: the bases above, the base highlighted,
 * the title starts out as given, the body empty and the draft box below them unticked; Enter in the title or Ctrl+S
 * anywhere creates it, Escape cancels.
 */
class NewPullRequestDialogTest {

    private static final Rect THEMED_AREA = new Rect(0, 0, 120, 30);

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private Model model = TestModels.loaded()
            .openPopup(new NewPullRequestPopup(
                    new BranchName("feature"),
                    new RemoteBranch("origin", new BranchName("feature")),
                    List.of(new BranchName("main"), new BranchName("release"), new BranchName("ahead")),
                    new BranchName("main"),
                    "Add login",
                    new NewPullRequest("Add login", "", false)));
    // As in the app, an edit or a move changes the model the popup shows; the other messages are only kept.
    private final NewPullRequestDialog dialog = new NewPullRequestDialog(msg -> {
        dispatched.add(msg);
        if (msg instanceof CreatePullRequestMsg.PullRequestEdited || msg instanceof CreatePullRequestMsg.BaseMoved) {
            model = Update.update(model, msg).model();
        }
    });

    private static void type(ToolkitTestRunner runner, String text) throws Exception {
        for (char c : text.toCharArray()) {
            SettledPilot.of(runner).press(c);
        }
        SettledPilot.of(runner).pause();
    }

    @Test
    void showsTheBasesThenTheTitleTheBodyAndTheDraftBox() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);

            assertTrue(screen.contains("Create pull request: 'feature'"), screen);
            assertTrue(screen.contains("Into: main"), screen);
            assertTrue(screen.indexOf("main") < screen.indexOf("release"), screen);
            assertTrue(screen.indexOf("release") < screen.indexOf("ahead"), screen);
            assertTrue(screen.indexOf("ahead") < screen.indexOf("Title"), screen);
            assertTrue(screen.contains("Add login"), screen);
            assertTrue(screen.indexOf("Title") < screen.indexOf("Body"), screen);
            assertTrue(screen.indexOf("Body") < screen.indexOf("[ ] Draft?"), screen);
            // The keys are told in the border of the popup, under the draft box.
            assertTrue(
                    screen.lines()
                            .anyMatch(line -> line.strip().startsWith("╰")
                                    && line.contains("─tab: next field  ↑↓: base  type: filter  space: tick draft╯")),
                    screen);
            assertTrue(screen.indexOf("[ ] Draft?") < screen.indexOf("tab: next field"), screen);
        }
    }

    @Test
    void enterInTheTitleCreatesItAsIs() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new CreatePullRequestMsg.Confirmed()), dispatched);
    }

    @Test
    void whatIsTypedAndTickedGoesToTheModel() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            // The app's global Ctrl+S opens the settings, after the fields had their chance.
            runner.runner()
                    .eventRouter()
                    .addGlobalHandler(event -> event instanceof KeyEvent key && key.hasCtrl() && key.isChar('s')
                            ? EventResult.HANDLED
                            : EventResult.UNHANDLED);
            type(runner, "!");
            SettledPilot.of(runner).press(KeyCode.TAB);
            type(runner, "Why");
            SettledPilot.of(runner).press(KeyCode.ENTER);
            type(runner, "it matters");
            SettledPilot.focus(runner, NewPullRequestDialog.DRAFT_ID);
            SettledPilot.of(runner).press(' ');
            SettledPilot.of(runner).pause();
            String screen = RenderedText.ofLarge(runner, this::view);
            assertTrue(screen.contains("[x] Draft?"), screen);
            SettledPilot.of(runner).press('s', KeyModifiers.CTRL);
            SettledPilot.of(runner).pause();
        }

        assertEquals(
                Option.of(new NewPullRequest("Add login!", "Why\nit matters", true)),
                model.popup(NewPullRequestPopup.class).map(NewPullRequestPopup::pullRequest));
        assertEquals(new CreatePullRequestMsg.Confirmed(), dispatched.getLast());
    }

    @Test
    void tabGoesRoundTheFieldsWithoutLeavingThePopup() throws Exception {
        // As in the app: the panes behind the popup are focusable when they are not covered.
        java.util.function.Supplier<Element> screen =
                () -> stack(new Dimmed(panel(text("files")).id("files").focusable(), Theme.Backdrop.DIM_ONLY), view());
        java.util.List<String> focused = new ArrayList<>();
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(screen::get)) {
            SettledPilot.focus(runner, NewPullRequestDialog.ID);
            RenderedText.ofLarge(runner, screen::get);
            for (int i = 0; i < 4; i++) {
                SettledPilot.of(runner).press(KeyCode.TAB);
                SettledPilot.of(runner).pause();
                focused.add(runner.runner().focusManager().focusedId());
            }
            SettledPilot.of(runner).press(KeyCode.TAB, KeyModifiers.SHIFT);
            SettledPilot.of(runner).pause();
            focused.add(runner.runner().focusManager().focusedId());
        }

        assertEquals(
                java.util.List.of(
                        NewPullRequestDialog.BODY_ID,
                        NewPullRequestDialog.DRAFT_ID,
                        NewPullRequestDialog.BASES_ID,
                        NewPullRequestDialog.ID,
                        NewPullRequestDialog.BASES_ID),
                focused);
    }

    @Test
    void escapeCancelsFromAnyField() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            // The app has a global Escape, which gets the key before the dialog around the fields does.
            runner.runner()
                    .eventRouter()
                    .addGlobalHandler(event -> event instanceof KeyEvent key && key.code() == KeyCode.ESCAPE
                            ? EventResult.HANDLED
                            : EventResult.UNHANDLED);
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.focus(runner, NewPullRequestDialog.BODY_ID);
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.focus(runner, NewPullRequestDialog.DRAFT_ID);
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.focus(runner, NewPullRequestDialog.BASES_ID);
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(
                java.util.List.of(
                        new Msg.PopupDismissed(NewPullRequestPopup.class),
                        new Msg.PopupDismissed(NewPullRequestPopup.class),
                        new Msg.PopupDismissed(NewPullRequestPopup.class),
                        new Msg.PopupDismissed(NewPullRequestPopup.class)),
                dispatched);
    }

    @Test
    void theArrowsInTheBasesPickTheBase() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.focus(runner, NewPullRequestDialog.BASES_ID);
            SettledPilot.of(runner).press(KeyCode.DOWN);
            SettledPilot.of(runner).pause();
            String screen = RenderedText.ofLarge(runner, this::view);
            assertTrue(screen.contains("Into: release"), screen);
        }

        assertEquals(
                java.util.List.of(new CreatePullRequestMsg.BaseMoved(
                        ListCursor.START.at(1), Option.of(new BranchName("release")))),
                dispatched);
    }

    @Test
    void typingInTheBasesFiltersThemAndPicksTheFirstShown() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.focus(runner, NewPullRequestDialog.BASES_ID);
            type(runner, "rel");
            String screen = RenderedText.ofLarge(runner, this::view);

            assertTrue(screen.contains("Into: release · filter: rel"), screen);
            assertTrue(!screen.contains("main"), screen);
            assertTrue(!screen.contains("ahead"), screen);
        }

        assertEquals(
                Option.of(new BranchName("release")),
                model.popup(NewPullRequestPopup.class).map(NewPullRequestPopup::base));
    }

    @Test
    void aFilterThatShowsNoBaseKeepsTheBaseInTheBorder() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.focus(runner, NewPullRequestDialog.BASES_ID);
            type(runner, "zz");
            String screen = RenderedText.ofLarge(runner, this::view);

            assertTrue(screen.contains("Into: main · filter: zz"), screen);
            assertTrue(screen.contains("No matches"), screen);
        }

        assertEquals(
                Option.of(new BranchName("main")),
                model.popup(NewPullRequestPopup.class).map(NewPullRequestPopup::base));
    }

    @Test
    void theFilterOfTheBasesOnlyTakesWhatIsTypedAtItsEnd() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.focus(runner, NewPullRequestDialog.BASES_ID);
            type(runner, "rel");
            // There is no cursor shown to move, so the arrows to the sides do nothing.
            SettledPilot.of(runner).press(KeyCode.LEFT);
            type(runner, "x");
            String typed = RenderedText.ofLarge(runner, this::view);
            SettledPilot.of(runner).press(KeyCode.BACKSPACE);
            SettledPilot.of(runner).pause();
            String erased = RenderedText.ofLarge(runner, this::view);
            SettledPilot.of(runner).press('u', KeyModifiers.CTRL);
            SettledPilot.of(runner).pause();
            String cleared = RenderedText.ofLarge(runner, this::view);

            assertTrue(typed.contains("filter: relx"), typed);
            assertTrue(erased.contains("Into: release · filter: rel"), erased);
            assertTrue(!cleared.contains("filter:"), cleared);
            assertTrue(cleared.contains("main"), cleared);
        }
    }

    @Test
    void escapeInTheBasesClearsTheFilterBeforeItCancels() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.focus(runner, NewPullRequestDialog.BASES_ID);
            type(runner, "rel");
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
            String screen = RenderedText.ofLarge(runner, this::view);

            assertTrue(screen.contains("Into: release"), screen);
            assertTrue(screen.contains("main"), screen);
            assertTrue(!screen.contains("filter:"), screen);
            assertTrue(dispatched.stream().noneMatch(Msg.PopupDismissed.class::isInstance), screen);
        }
    }

    @Test
    void enterInTheBasesGoesOnToTheTitle() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.focus(runner, NewPullRequestDialog.BASES_ID);
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();

            assertEquals(NewPullRequestDialog.ID, runner.runner().focusManager().focusedId());
        }

        assertEquals(java.util.List.of(), dispatched);
    }

    private ToolkitTestRunner start() throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        SettledPilot.focus(runner, NewPullRequestDialog.ID);
        RenderedText.ofLarge(runner, this::view);
        return runner;
    }

    @Test
    void theFieldWithTheFocusIsBorderedAsThePaneInUse() {
        Buffer title = themed(NewPullRequestDialog.ID);
        Buffer bases = themed(NewPullRequestDialog.BASES_ID);

        assertEquals(Optional.of(Color.CYAN), border(title, "Title"));
        assertEquals(Optional.of(Color.DARK_GRAY), border(title, "Into: main"));
        assertEquals(Optional.of(Color.CYAN), border(bases, "Into: main"));
        assertEquals(Optional.of(Color.DARK_GRAY), border(bases, "Title"));
    }

    /**
     * The popup in the dark theme, while {@code focused} has the focus.
     */
    private Buffer themed(String focused) {
        FocusManager focus = new FocusManager();
        focus.setFocus(focused);
        DefaultRenderContext context = new DefaultRenderContext(focus, new EventRouter(focus, new ElementRegistry()));
        context.setStyleEngine(Theme.createEngine().get());
        Buffer buffer = Buffer.empty(THEMED_AREA);
        view().render(Frame.forTesting(buffer), THEMED_AREA, context);
        return buffer;
    }

    private static Optional<Color> border(Buffer buffer, String title) {
        return cornerBefore(buffer, THEMED_AREA, title).style().fg();
    }

    /**
     * The cell left of where {@code text} starts on the screen: the corner of the border it is the title of.
     */
    private static dev.tamboui.buffer.Cell cornerBefore(Buffer buffer, Rect area, String text) {
        for (int y = 0; y < area.height(); y++) {
            StringBuilder line = new StringBuilder();
            for (int x = 0; x < area.width(); x++) {
                line.append(buffer.get(x, y).symbol());
            }
            int at = line.indexOf(text);
            if (at > 0) {
                return buffer.get(at - 1, y);
            }
        }
        throw new AssertionError(text + " not shown");
    }

    private Element view() {
        return dialog.render(model).get();
    }
}
