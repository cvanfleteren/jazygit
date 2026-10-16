package net.vanfleteren.jazygit.feature.commit;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import java.time.Instant;
import java.util.ArrayList;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.ui.RenderedText;
import org.junit.jupiter.api.Test;

/**
 * The popup asking to confirm squashing a commit into the one below it: Enter confirms, Escape cancels.
 */
class SquashDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final net.vanfleteren.jazygit.ui.widgets.ConfirmDialog<SquashPopup> dialog =
            new net.vanfleteren.jazygit.ui.widgets.ConfirmDialog<>(CommitDialogs.SQUASH, dispatched::add);
    private final Model open = openModel();

    private static Model openModel() {
        Commit below = new Commit(new Sha("c1"), "Ada", "ada@example.com", Instant.EPOCH, "Below", "");
        Commit selected = new Commit(
                new Sha("c2"), "Ada", "ada@example.com", Instant.EPOCH, "Selected", "", List.of(new Sha("c1")));
        Model model = Update.update(TestModels.loaded(), new LoadMsg.CommitsLoaded(List.of(selected, below)))
                .model();
        return Update.update(model, new SquashMsg.Requested(selected.sha())).model();
    }

    @Test
    void enterConfirms() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            runner.pilot().press(KeyCode.ENTER);
            runner.pilot().pause();
        }

        assertEquals(java.util.List.of(new SquashMsg.Confirmed()), dispatched);
    }

    @Test
    void escapeCancels() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            runner.pilot().press(KeyCode.ESCAPE);
            runner.pilot().pause();
        }

        assertEquals(java.util.List.of(new SquashMsg.Cancelled()), dispatched);
    }

    @Test
    void showsTitleAndExplanation() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);
            org.junit.jupiter.api.Assertions.assertTrue(screen.contains("Squash commit"), screen);
            org.junit.jupiter.api.Assertions.assertTrue(
                    screen.contains("Are you sure you want to squash the selected commit"), screen);
            org.junit.jupiter.api.Assertions.assertTrue(screen.contains("the commit below?"), screen);
        }
    }

    @Test
    void closedModelShowsNoPopup() {
        assertTrue(dialog.render(TestModels.loaded()).isEmpty());
    }

    private ToolkitTestRunner start() throws Exception {
        assertTrue(open.popup(SquashPopup.class).isDefined());
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        runner.runner().focusManager().setFocus(CommitDialogs.SQUASH.id());
        RenderedText.of(runner, this::view);
        return runner;
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).get());
    }
}
