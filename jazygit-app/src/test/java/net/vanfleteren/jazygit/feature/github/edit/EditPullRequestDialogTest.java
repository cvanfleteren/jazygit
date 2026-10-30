package net.vanfleteren.jazygit.feature.github.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.ArrayList;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.ListCursor;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import org.junit.jupiter.api.Test;

/**
 * The popup editing a pull request: laid out and keyed as the one that creates one, with the bases above, then the
 * title, the body and the draft box; a base or draft box that cannot be changed is dimmed and skipped by Tab. Enter in
 * the title or Ctrl+S anywhere saves, Ctrl+O opens the body in the editor, and Escape goes back.
 */
class EditPullRequestDialogTest {

    private static final RemoteBranch MAIN = new RemoteBranch("origin", new BranchName("main"));
    private static final RemoteBranch RELEASE = new RemoteBranch("origin", new BranchName("release"));
    private static final PullRequestDetails DETAILS =
            StubGitHub.empty().pullRequestDetails(42).get().withBody("Why");
    private static final PullRequestPopup SHOWN = PullRequestPopup.loading(
                    new BranchName("feature"),
                    new PullRequest(42, "feature", false, Checks.PASSING, Review.APPROVED),
                    Instant.EPOCH,
                    0)
            .with(Either.right(DETAILS), 0);
    private static final PullRequestFields OPENED = new PullRequestFields("Add login", "Why", MAIN, false);
    private static final EditPullRequestPopup EDITING =
            new EditPullRequestPopup(SHOWN, List.of(MAIN, RELEASE), OPENED, OPENED, true, true, false);

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private Model model = TestModels.loaded().openPopup(EDITING);
    // As in the app, an edit or a move changes the model the popup shows; the other messages are only kept.
    private final EditPullRequestDialog dialog = new EditPullRequestDialog(msg -> {
        dispatched.add(msg);
        if (msg instanceof EditPullRequestMsg.Edited || msg instanceof EditPullRequestMsg.BaseMoved) {
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

            assertTrue(screen.contains("Edit pull request #42: 'feature'"), screen);
            assertTrue(screen.contains("Into: main"), screen);
            assertTrue(screen.indexOf("Into: main") < screen.indexOf("release"), screen);
            assertTrue(screen.indexOf("release") < screen.indexOf("Title"), screen);
            assertTrue(screen.contains("Add login"), screen);
            assertTrue(screen.indexOf("Title") < screen.indexOf("Why"), screen);
            assertTrue(screen.indexOf("Why") < screen.indexOf("[ ] Draft?"), screen);
            assertTrue(screen.contains("ctrl+s: save  ctrl+o: body in editor"), screen);
        }
    }

    @Test
    void whatIsTypedAndTickedGoesToTheModelAndCtrlSSavesIt() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            // The app's global Ctrl+S opens the settings, after the fields had their chance.
            runner.runner()
                    .eventRouter()
                    .addGlobalHandler(event -> event instanceof KeyEvent key && key.hasCtrl() && key.isChar('s')
                            ? EventResult.HANDLED
                            : EventResult.UNHANDLED);
            type(runner, "!");
            SettledPilot.of(runner).press(KeyCode.TAB);
            SettledPilot.of(runner).press(KeyCode.END);
            SettledPilot.of(runner).press(KeyCode.ENTER);
            type(runner, "it matters");
            SettledPilot.focus(runner, EditPullRequestDialog.DRAFT_ID);
            SettledPilot.of(runner).press(' ');
            SettledPilot.of(runner).press('s', KeyModifiers.CTRL);
            SettledPilot.of(runner).pause();
        }

        assertEquals(
                Option.of(new PullRequestFields("Add login!", "Why\nit matters", MAIN, true)),
                model.popup(EditPullRequestPopup.class).map(EditPullRequestPopup::fields));
        assertEquals(new EditPullRequestMsg.Confirmed(), dispatched.getLast());
    }

    @Test
    void enterInTheTitleSaves() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new EditPullRequestMsg.Confirmed()), dispatched);
    }

    @Test
    void ctrlOOpensTheBodyInTheEditor() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.focus(runner, EditPullRequestDialog.BODY_ID);
            SettledPilot.of(runner).press('o', KeyModifiers.CTRL);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new EditPullRequestMsg.EditorRequested()), dispatched);
    }

    @Test
    void theArrowsInTheBasesPickTheBase() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.focus(runner, EditPullRequestDialog.BASES_ID);
            SettledPilot.of(runner).press(KeyCode.DOWN);
            SettledPilot.of(runner).pause();
            String screen = RenderedText.ofLarge(runner, this::view);
            assertTrue(screen.contains("Into: release"), screen);
        }

        assertEquals(
                java.util.List.of(new EditPullRequestMsg.BaseMoved(ListCursor.START.at(1), Option.of(RELEASE))),
                dispatched);
    }

    @Test
    void escapeClearsTheFilterOfTheBasesAndElseGoesBack() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            // The app has a global Escape, which gets the key before the dialog around the fields does.
            runner.runner()
                    .eventRouter()
                    .addGlobalHandler(event -> event instanceof KeyEvent key && key.code() == KeyCode.ESCAPE
                            ? EventResult.HANDLED
                            : EventResult.UNHANDLED);
            SettledPilot.focus(runner, EditPullRequestDialog.BASES_ID);
            type(runner, "rel");
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
            String screen = RenderedText.ofLarge(runner, this::view);
            assertFalse(screen.contains("filter:"), screen);
            assertTrue(dispatched.stream().noneMatch(EditPullRequestMsg.Cancelled.class::isInstance), screen);
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.focus(runner, EditPullRequestDialog.BODY_ID);
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(
                java.util.List.of(new EditPullRequestMsg.Cancelled(), new EditPullRequestMsg.Cancelled()),
                dispatched.stream()
                        .filter(EditPullRequestMsg.Cancelled.class::isInstance)
                        .toList());
    }

    @Test
    void aBaseAndDraftThatCannotBeChangedAreDimmedAndTabGoesPastThem() throws Exception {
        model = TestModels.loaded().openPopup(EDITING.withBaseChangeable(false).withDraftChangeable(false));
        java.util.List<String> focused = new ArrayList<>();
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);
            assertTrue(screen.contains("Into: main"), screen);
            assertTrue(screen.contains("stacked on another pull request"), screen);
            assertFalse(screen.contains("release"), screen);
            for (int i = 0; i < 2; i++) {
                SettledPilot.of(runner).press(KeyCode.TAB);
                SettledPilot.of(runner).pause();
                focused.add(runner.runner().focusManager().focusedId());
            }
        }

        assertEquals(java.util.List.of(EditPullRequestDialog.BODY_ID, EditPullRequestDialog.ID), focused);
    }

    @Test
    void whileItIsSavedItSaysSo() throws Exception {
        model = TestModels.loaded().openPopup(EDITING.withSaving(true));
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);

            assertTrue(screen.contains("Saving the pull request…"), screen);
        }
    }

    private ToolkitTestRunner start() throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        SettledPilot.focus(runner, EditPullRequestDialog.ID);
        RenderedText.ofLarge(runner, this::view);
        return runner;
    }

    private Element view() {
        return dialog.render(model).get();
    }
}
