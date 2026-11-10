package net.vanfleteren.jazygit.feature.github.verdict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
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
 * The popup asking for a review of a pull request (ADR 99): its title says what it does, Ctrl+T switches it, and a
 * draft says so.
 */
class VerdictDialogTest {

    private static final VerdictPopup REVIEW = VerdictPopup.ofDiff(42, new Reviewing("PR_42", false, false), false);

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private Model model = TestModels.loaded().openPopup(REVIEW);
    // As in the app, what is dispatched changes the model the popup shows.
    private final VerdictDialog dialog = new VerdictDialog(msg -> {
        dispatched.add(msg);
        if (msg instanceof VerdictMsg.Edited || msg instanceof VerdictMsg.Switched) {
            model = Update.update(model, msg).model();
        }
    });

    @Test
    void itSaysWhatItSendsAndTheKeys() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);

            assertTrue(screen.contains("Review pull request #42 · approve"), screen);
            assertTrue(screen.contains("ctrl+t: approve or request changes"), screen);
            assertFalse(screen.contains("still a draft"), screen);
        }
    }

    @Test
    void ctrlTSwitchesToAskingForChanges() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press('t', KeyModifiers.CTRL);
            SettledPilot.of(runner).pause();
            assertEquals(java.util.List.of(new VerdictMsg.Switched()), dispatched);
            String screen = RenderedText.ofLarge(runner, this::view);

            assertTrue(screen.contains("Review pull request #42 · request changes"), screen);
        }

        assertEquals(
                Option.of(Verdict.REQUEST_CHANGES),
                model.popup(VerdictPopup.class).map(VerdictPopup::verdict));
    }

    @Test
    void aDraftSaysSoInTheTitleAndAboveTheText() throws Exception {
        model = model.openPopup(REVIEW.withDraft(true));
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);

            assertTrue(screen.contains("Review pull request #42 · draft · approve"), screen);
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
        SettledPilot.focus(runner, VerdictDialog.ID);
        RenderedText.ofLarge(runner, this::view);
        return runner;
    }

    private Element view() {
        return dialog.render(model).get();
    }
}
