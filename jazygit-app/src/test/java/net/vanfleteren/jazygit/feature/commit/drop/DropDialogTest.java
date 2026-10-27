package net.vanfleteren.jazygit.feature.commit.drop;

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
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import org.junit.jupiter.api.Test;

/**
 * The popup asking to confirm dropping a commit: Enter confirms, Escape cancels.
 */
class DropDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final net.vanfleteren.jazygit.ui.widgets.ConfirmDialog<DropPopup> dialog =
            new net.vanfleteren.jazygit.ui.widgets.ConfirmDialog<>(DropDialogs.DROP, dispatched::add);
    private final Model open = openModel();

    private static Model openModel() {
        Commit below = Fixtures.commit(new Sha("c1"), "Ada", "ada@example.com", Instant.EPOCH, "Below", "");
        Commit selected = Fixtures.commit(
                new Sha("c2"), "Ada", "ada@example.com", Instant.EPOCH, "Selected", "", List.of(new Sha("c1")));
        Model model = Update.update(TestModels.loaded(), TestModels.commitsLoaded(List.of(selected, below)))
                .model();
        return Update.update(model, new DropMsg.Requested(selected.sha())).model();
    }

    @Test
    void enterConfirms() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new DropMsg.Confirmed()), dispatched);
    }

    @Test
    void escapeCancels() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }

        assertEquals(java.util.List.of(new Msg.PopupDismissed(DropPopup.class)), dispatched);
    }

    @Test
    void showsTitleAndExplanation() throws Exception {
        try (ToolkitTestRunner runner = start()) {
            String screen = RenderedText.ofLarge(runner, this::view);
            org.junit.jupiter.api.Assertions.assertTrue(screen.contains("Drop commit"), screen);
            org.junit.jupiter.api.Assertions.assertTrue(
                    screen.contains("Are you sure you want to drop the commit c2"), screen);
            org.junit.jupiter.api.Assertions.assertTrue(screen.contains("\"Selected\"?"), screen);
        }
    }

    @Test
    void closedModelShowsNoPopup() {
        assertTrue(dialog.render(TestModels.loaded()).isEmpty());
    }

    private ToolkitTestRunner start() throws Exception {
        assertTrue(open.popup(DropPopup.class).isDefined());
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        SettledPilot.focus(runner, DropDialogs.DROP.id());
        RenderedText.of(runner, this::view);
        return runner;
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).get());
    }
}
