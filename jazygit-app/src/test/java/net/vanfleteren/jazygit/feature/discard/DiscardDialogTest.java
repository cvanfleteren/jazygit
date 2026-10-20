package net.vanfleteren.jazygit.feature.discard;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import java.util.ArrayList;
import net.vanfleteren.jazygit.feature.discard.DiscardMsg.Scope;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.state.CursorFeedback;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import org.junit.jupiter.api.Test;

/**
 * The popup asking what to discard.
 */
class DiscardDialogTest {

    private static final FileEntry MODIFIED = new FileEntry("a.txt", ChangeType.MODIFIED);
    private static final FileEntry STAGED = new FileEntry("b.txt", ChangeType.ADDED);

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final CursorFeedback cursor = new CursorFeedback(dispatched::add);
    private final net.vanfleteren.jazygit.ui.widgets.ChoiceDialog<DiscardPopup> dialog =
            new net.vanfleteren.jazygit.ui.widgets.ChoiceDialog<>(DiscardDialog.SPEC, cursor);

    private static Model open(FileEntry... files) {
        return Update.update(TestModels.loaded(), new DiscardMsg.Requested(List.of(files)))
                .model();
    }

    @Test
    void lettersChooseAnOption() throws Exception {
        assertEquals(
                List.of(new DiscardMsg.Chosen(Scope.ALL)),
                pressing(open(MODIFIED), r -> SettledPilot.of(r).press('x')));
        dispatched.clear();
        assertEquals(
                List.of(new DiscardMsg.Chosen(Scope.UNSTAGED)),
                pressing(open(MODIFIED), r -> SettledPilot.of(r).press('u')));
    }

    @Test
    void arrowsAndEnterChooseTheSelectedLine() throws Exception {
        assertEquals(
                List.of(new DiscardMsg.Chosen(Scope.ALL)),
                pressing(open(MODIFIED), r -> SettledPilot.of(r).press(KeyCode.ENTER)));
        dispatched.clear();
        assertEquals(List.of(new DiscardMsg.Chosen(Scope.UNSTAGED)), pressing(open(MODIFIED), r -> {
            SettledPilot.of(r).press(KeyCode.DOWN);
            SettledPilot.of(r).press(KeyCode.ENTER);
        }));
    }

    @Test
    void escapeCancels() throws Exception {
        assertEquals(
                List.of(new DiscardMsg.Cancelled()),
                pressing(open(MODIFIED), r -> SettledPilot.of(r).press(KeyCode.ESCAPE)));
    }

    @Test
    void theUnstagedOptionIsNotActionableWithoutUnstagedChanges() throws Exception {
        assertEquals(List.of(), pressing(open(STAGED), r -> {
            SettledPilot.of(r).press('u');
            SettledPilot.of(r).press(KeyCode.DOWN);
            SettledPilot.of(r).press(KeyCode.ENTER);
        }));
        dispatched.clear();
        assertEquals(
                List.of(new DiscardMsg.Chosen(Scope.ALL)),
                pressing(open(STAGED), r -> SettledPilot.of(r).press('x')));
    }

    @Test
    void showsTheTitleAndTheOptions() throws Exception {
        Model model = open(MODIFIED);
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(model))) {
            String screen = RenderedText.ofLarge(runner, () -> view(model));
            assertTrue(screen.contains("Discard changes"), screen);
            assertTrue(screen.contains("(x) Discard all changes"), screen);
            assertTrue(screen.contains("(u) Discard unstaged changes"), screen);
            assertTrue(screen.contains("(enter) select   (esc) cancel"), screen);
        }
    }

    private List<Msg> pressing(Model model, Keys keys) throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(model))) {
            SettledPilot.focus(runner, DiscardDialog.SPEC.id());
            RenderedText.of(runner, () -> view(model));
            keys.press(runner);
            SettledPilot.of(runner).pause();
        }
        return List.ofAll(dispatched);
    }

    private Element view(Model model) {
        return stack(text("background"), dialog.render(cursor.on(model)).get());
    }

    @FunctionalInterface
    private interface Keys {
        void press(ToolkitTestRunner runner) throws Exception;
    }
}
