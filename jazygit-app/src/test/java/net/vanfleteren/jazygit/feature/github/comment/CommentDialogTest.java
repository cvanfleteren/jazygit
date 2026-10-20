package net.vanfleteren.jazygit.feature.github.comment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.ArrayList;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import org.junit.jupiter.api.Test;

/**
 * The popup asking for a comment on a pull request: what is typed goes to the model, Ctrl+S sends it, Ctrl+O opens it
 * in the editor and Escape goes back.
 */
class CommentDialogTest {

    private static final PullRequestPopup SHOWN = PullRequestPopup.loading(
                    new Branch.Name("feature"), new PullRequest(42, "feature", false, Checks.PASSING, Review.APPROVED))
            .with(Either.right(StubGitHub.empty().pullRequestDetails(42).get()));

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private Model model = TestModels.loaded().openPopup(CommentPopup.on(SHOWN));
    // As in the app, an edit changes the model the popup shows; the other messages are only kept.
    private final CommentDialog dialog = new CommentDialog(msg -> {
        dispatched.add(msg);
        if (msg instanceof CommentMsg.Edited) {
            model = Update.update(model, msg).model();
        }
    });

    @Test
    void showsWhichPullRequestAndTheKeys() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);

            assertTrue(screen.contains("Comment on pull request #42"), screen);
            assertTrue(screen.contains("ctrl+s: send · ctrl+o: open in editor"), screen);
        }
    }

    @Test
    void whatIsTypedGoesToTheModelAndCtrlSSendsIt() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            // The app's global Ctrl+S opens the settings, after the text area had its chance.
            runner.runner()
                    .eventRouter()
                    .addGlobalHandler(event -> event instanceof KeyEvent key && key.hasCtrl() && key.isChar('s')
                            ? EventResult.HANDLED
                            : EventResult.UNHANDLED);
            for (char c : "Nice".toCharArray()) {
                SettledPilot.of(runner).press(c);
            }
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).press('!');
            SettledPilot.of(runner).press('s', KeyModifiers.CTRL);
            SettledPilot.of(runner).pause();
        }

        assertEquals(Option.of("Nice\n!"), model.popup(CommentPopup.class).map(CommentPopup::text));
        assertEquals(new CommentMsg.Confirmed(), dispatched.getLast());
    }

    @Test
    void ctrlOOpensItInTheEditorAndEscapeGoesBack() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            // The app has a global Escape, which gets the key before the dialog around the text area does.
            runner.runner()
                    .eventRouter()
                    .addGlobalHandler(event -> event instanceof KeyEvent key && key.code() == KeyCode.ESCAPE
                            ? EventResult.HANDLED
                            : EventResult.UNHANDLED);
            SettledPilot.of(runner).press('o', KeyModifiers.CTRL);
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new CommentMsg.EditorRequested(), new CommentMsg.Cancelled()), dispatched);
    }

    @Test
    void whileSendingItSaysSoAndAFailureSaysWhy() throws Exception {
        model = model.openPopup(CommentPopup.on(SHOWN).withText("Nice").withSending(true))
                .fail(new AppError.NotPushed(new Branch.Name("feature")));
        try (ToolkitTestRunner runner = start()) {
            String sending = RenderedText.ofLarge(runner, this::view);
            assertTrue(sending.contains("Sending the comment…"), sending);
            assertFalse(sending.contains("Push branch feature"), sending);

            model = model.openPopup(CommentPopup.on(SHOWN).withText("Nice"));
            String failed = RenderedText.ofLarge(runner, this::view);
            assertTrue(failed.contains("Push branch feature"), failed);
        }
    }

    private ToolkitTestRunner start() throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        SettledPilot.focus(runner, CommentDialog.ID);
        RenderedText.ofLarge(runner, this::view);
        return runner;
    }

    private Element view() {
        return dialog.render(model).get();
    }
}
