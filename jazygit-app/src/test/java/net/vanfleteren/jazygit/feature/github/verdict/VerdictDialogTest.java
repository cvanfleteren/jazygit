package net.vanfleteren.jazygit.feature.github.verdict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import io.vavr.control.Option;
import java.util.ArrayList;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Reviewing;
import net.vanfleteren.jazygit.gh.model.Verdict;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.AppError.CannotReview.Why;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import org.junit.jupiter.api.Test;

/**
 * The popup asking for a review of a pull request (ADR 99): a choice of approving or asking for changes above the
 * text, which Tab goes through and Space chooses from, and a draft that says so.
 */
class VerdictDialogTest {

    private static final VerdictPopup REVIEW = VerdictPopup.ofDiff(42, new Reviewing("PR_42", false, false), false);

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private Model model = TestModels.loaded().openPopup(REVIEW);
    // As in the app, what is chosen or typed changes the model the popup shows; the other messages are only kept.
    private final VerdictDialog dialog = new VerdictDialog(msg -> {
        dispatched.add(msg);
        if (msg instanceof VerdictMsg.Edited || msg instanceof VerdictMsg.Chosen) {
            model = Update.update(model, msg).model();
        }
    });

    @Test
    void itShowsTheChoiceApprovingAndTheKeys() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);

            assertTrue(screen.contains("Review pull request #42"), screen);
            assertTrue(screen.contains("(•) Approve   ( ) Request changes"), screen);
            assertTrue(
                    screen.contains(
                            "←→ in Verdict: choose · tab: next field · ctrl+s: submit · ctrl+o: open in editor"),
                    screen);
            assertFalse(screen.contains("still a draft"), screen);
        }
    }

    @Test
    void theChoicesAndTheTextAreInBoxesOfTheirOwnWhoseTitleSaysWhetherTheTextIsNeeded() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            String approving = RenderedText.ofLarge(runner, this::view);
            assertTrue(approving.contains("┌Verdict"), approving);
            assertTrue(approving.contains("│(•) Approve   ( ) Request changes"), approving);
            assertTrue(approving.contains("┌Comment (optional)"), approving);

            model = model.openPopup(REVIEW.withVerdict(Verdict.REQUEST_CHANGES));
            String asking = RenderedText.ofLarge(runner, this::view);
            assertTrue(asking.contains("┌Reason (required)"), asking);
        }
    }

    @Test
    void theArrowsChooseAndTabGoesOnToTheText() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(KeyCode.RIGHT);
            SettledPilot.of(runner).pause();
            String chosen = RenderedText.ofLarge(runner, this::view);
            assertTrue(chosen.contains("( ) Approve   (•) Request changes"), chosen);

            // The focus went along with the mark: the arrows still choose.
            SettledPilot.of(runner).press(KeyCode.LEFT);
            SettledPilot.of(runner).pause();
            String back = RenderedText.ofLarge(runner, this::view);
            assertTrue(back.contains("(•) Approve   ( ) Request changes"), back);
            SettledPilot.of(runner).press(KeyCode.DOWN);
            SettledPilot.of(runner).pause();

            SettledPilot.of(runner).press(KeyCode.TAB);
            for (char c : "Rename it".toCharArray()) {
                SettledPilot.of(runner).press(c);
            }
            SettledPilot.of(runner).pause();
        }

        assertEquals(new VerdictMsg.Chosen(Verdict.REQUEST_CHANGES), dispatched.getFirst());
        assertEquals(
                Option.of(Verdict.REQUEST_CHANGES),
                model.popup(VerdictPopup.class).map(VerdictPopup::verdict));
        assertEquals(Option.of("Rename it"), model.popup(VerdictPopup.class).map(VerdictPopup::text));
    }

    @Test
    void ctrlSSendsItFromAChoice() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            // The app's global Ctrl+S opens the settings, after the focused part had its chance.
            runner.runner()
                    .eventRouter()
                    .addGlobalHandler(event -> event instanceof KeyEvent key && key.hasCtrl() && key.isChar('s')
                            ? EventResult.HANDLED
                            : EventResult.UNHANDLED);
            SettledPilot.of(runner).press('s', KeyModifiers.CTRL);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new VerdictMsg.Confirmed()), dispatched);
    }

    @Test
    void aDraftSaysSoInTheTitleAndAboveTheText() throws Exception {
        model = model.openPopup(REVIEW.withDraft(true));
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);

            assertTrue(screen.contains("Review pull request #42 · draft"), screen);
            assertTrue(screen.contains("This pull request is still a draft"), screen);
        }
    }

    @Test
    void whyItCannotBeSentShowsUnderTheText() throws Exception {
        model = model.openPopup(REVIEW.withVerdict(Verdict.REQUEST_CHANGES))
                .fail(new AppError.CannotReview(Why.NO_REASON));
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);

            assertTrue(screen.contains("Say what should change"), screen);
        }
    }

    private ToolkitTestRunner start() throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        SettledPilot.focus(runner, VerdictDialog.CHOICES_ID);
        RenderedText.ofLarge(runner, this::view);
        return runner;
    }

    private Element view() {
        return dialog.render(model).get();
    }
}
