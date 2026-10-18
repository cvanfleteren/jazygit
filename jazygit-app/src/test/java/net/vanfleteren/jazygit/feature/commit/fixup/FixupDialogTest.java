package net.vanfleteren.jazygit.feature.commit.fixup;

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
import net.vanfleteren.jazygit.git.model.SquashMode;
import net.vanfleteren.jazygit.state.*;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import org.junit.jupiter.api.Test;

/**
 * The popup asking which message to keep when fixing up a commit into the one below it.
 */
class FixupDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final ChoiceDialog<FixupPopup> dialog = new ChoiceDialog<>(FixupDialogs.FIXUP, dispatched::add);
    private final Model open = openModel();

    private static Model openModel() {
        Commit below = new Commit(new Sha("1111111111"), "Ada", "ada@example.com", Instant.EPOCH, "Below", "");
        Commit selected = new Commit(
                new Sha("2222222222"), "Ada", "ada@example.com", Instant.EPOCH, "Selected", "", List.of(below.sha()));
        Model model = Update.update(TestModels.loaded(), new LoadMsg.CommitsLoaded(List.of(selected, below)))
                .model();
        return Update.update(model, new FixupMsg.Requested(selected.sha())).model();
    }

    @Test
    void listsBothWaysToFixUpAndCancel() throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view)) {
            String screen = RenderedText.ofLarge(runner, this::view);
            assertTrue(
                    screen.contains(
                            "Fixup " + new Sha("2222222222").shortSha() + " into " + new Sha("1111111111").shortSha()),
                    screen);
            assertTrue(screen.contains("(f) Fixup"), screen);
            assertTrue(screen.contains("(C) Fixup and use this commit's message"), screen);
            assertTrue(screen.contains("(c) Cancel"), screen);
        }
    }

    @Test
    void eachLetterChoosesItsWayToFixUp() throws Exception {
        assertEquals(List.of(new FixupMsg.Chosen(SquashMode.FIXUP)), pressing('f'));
        assertEquals(List.of(new FixupMsg.Chosen(SquashMode.FIXUP_KEEP_MESSAGE)), pressing('C'));
    }

    @Test
    void escapeAndCCancel() throws Exception {
        assertEquals(List.of(new FixupMsg.Cancelled()), pressing('c'));
        dispatched.clear();
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).pause();
        }
        assertEquals(java.util.List.of(new FixupMsg.Cancelled()), dispatched);
    }

    private List<Msg> pressing(char key) throws Exception {
        dispatched.clear();
        try (ToolkitTestRunner runner = start()) {
            SettledPilot.of(runner).press(key);
            SettledPilot.of(runner).pause();
        }
        return List.ofAll(dispatched);
    }

    private ToolkitTestRunner start() throws Exception {
        assertTrue(open.popup(FixupPopup.class).isDefined());
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(this::view);
        SettledPilot.focus(runner, FixupDialogs.FIXUP.id());
        RenderedText.of(runner, this::view);
        return runner;
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).get());
    }
}
