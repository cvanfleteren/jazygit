package net.vanfleteren.jazygit.feature.commit;

import static dev.tamboui.toolkit.Toolkit.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.ArrayList;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.ResetMode;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.LoadMsg;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import net.vanfleteren.jazygit.ui.widgets.ConfirmDialog;
import org.junit.jupiter.api.Test;

/**
 * The popups asking how to reset to a commit, and to confirm a hard reset.
 */
class ResetDialogTest {

    private static final Commit TARGET =
            new Commit(new Sha("1111111111"), "Ada", "ada@example.com", Instant.EPOCH, "Target", "");

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final ChoiceDialog<ResetPopup> dialog = new ChoiceDialog<>(CommitDialogs.RESET, dispatched::add);
    private final Model open = Update.update(
                    Update.update(TestModels.loaded(), new LoadMsg.CommitsLoaded(List.of(TARGET)))
                            .model(),
                    new ResetMsg.Requested(TARGET.sha()))
            .model();

    @Test
    void listsTheThreeResetsAndCancel() throws Exception {
        String screen =
                render(() -> stack(text("background"), dialog.render(open).get()));

        assertTrue(screen.contains("Reset to " + TARGET.sha().shortSha()), screen);
        assertTrue(screen.contains("(m) Mixed reset: keep changes, unstaged"), screen);
        assertTrue(screen.contains("(s) Soft reset: keep changes, staged"), screen);
        assertTrue(screen.contains("(h) Hard reset: discard all changes"), screen);
        assertTrue(screen.contains("(c) Cancel"), screen);
    }

    @Test
    void eachLetterChoosesItsReset() throws Exception {
        assertEquals(List.of(new ResetMsg.Chosen(ResetMode.MIXED)), pressing('m'));
        assertEquals(List.of(new ResetMsg.Chosen(ResetMode.SOFT)), pressing('s'));
        assertEquals(List.of(new ResetMsg.Chosen(ResetMode.HARD)), pressing('h'));
        assertEquals(List.of(new ResetMsg.Cancelled()), pressing('c'));
    }

    @Test
    void theHardResetConfirmationSaysHowManyFilesLoseTheirChanges() throws Exception {
        assertTrue(confirmation(Option.of(1)).contains("Discard the changes to 1 file?"));
        assertTrue(confirmation(Option.of(3)).contains("Discard the changes to 3 files?"));
        assertTrue(confirmation(Option.none()).contains("Discard all uncommitted changes?"));
        assertTrue(confirmation(Option.none())
                .contains("Hard reset to " + TARGET.sha().shortSha()));
    }

    private String confirmation(Option<Integer> files) throws Exception {
        ConfirmDialog<HardResetPopup> confirm = new ConfirmDialog<>(CommitDialogs.HARD_RESET, dispatched::add);
        Model asking = TestModels.loaded().openPopup(new HardResetPopup(TARGET, files));
        return render(() -> stack(text("background"), confirm.render(asking).get()));
    }

    private static String render(java.util.function.Supplier<Element> view) throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(view::get)) {
            return RenderedText.ofLarge(runner, view::get);
        }
    }

    private List<Msg> pressing(char key) throws Exception {
        dispatched.clear();
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view)) {
            runner.runner().focusManager().setFocus(CommitDialogs.RESET.id());
            RenderedText.of(runner, this::view);
            runner.pilot().press(key);
            runner.pilot().pause();
        }
        return List.ofAll(dispatched);
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).get());
    }
}
