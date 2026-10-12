package net.vanfleteren.jazygit.feature.theme;

import static dev.tamboui.toolkit.Toolkit.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

/**
 * The popup asking how to rebase the checked out branch.
 */
class ThemeDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final ChoiceDialog<ThemePopup> dialog = new net.vanfleteren.jazygit.ui.widgets.ChoiceDialog<>(ThemeDialog.SPEC, dispatched::add);
    private final Model open = Update.update(TestModels.loaded(new SampleData()),
            new ThemeMsg.Requested()).model();

    @Test
    void lettersChooseATheme() throws Exception {
        assertEquals(List.of(new ThemeMsg.Chosen("dark")), pressing(r -> r.pilot().press('d')));
        dispatched.clear();
        assertEquals(List.of(new ThemeMsg.Chosen("light")), pressing(r -> r.pilot().press('l')));
        dispatched.clear();
        assertEquals(List.of(new ThemeMsg.Chosen("darcula")), pressing(r -> r.pilot().press('a')));
    }

    @Test
    void arrowsAndEnterChooseTheSelectedLine() throws Exception {
        assertEquals(List.of(new ThemeMsg.Chosen("light")), pressing(r -> {
            r.pilot().press(KeyCode.DOWN);
            r.pilot().press(KeyCode.ENTER);
        }));
    }

    @Test
    void escapeAndCCancel() throws Exception {
        assertEquals(List.of(new ThemeMsg.Cancelled()), pressing(r -> r.pilot().press(KeyCode.ESCAPE)));
        dispatched.clear();
        assertEquals(List.of(new ThemeMsg.Cancelled()), pressing(r -> r.pilot().press('c')));
    }

    @Test
    void listsTheThemesAndMarksTheCurrentOne() throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view())) {
            String screen = RenderedText.ofLarge(runner, () -> view());
            assertTrue(screen.contains("(d) dark (current)"), screen);
            assertTrue(screen.contains("(l) light"), screen);
            assertTrue(screen.contains("(a) darcula"), screen);
            assertTrue(screen.contains("(c) Cancel"), screen);
        }
    }

    @FunctionalInterface
    private interface Keys {
        void press(ToolkitTestRunner runner) throws Exception;
    }

    private List<Msg> pressing(Keys keys) throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view())) {
            runner.runner().focusManager().setFocus(ThemeDialog.SPEC.id());
            RenderedText.of(runner, () -> view());
            keys.press(runner);
            runner.pilot().pause();
        }
        return List.ofAll(dispatched);
    }

    private Element view() {
        return stack(text("background"), dialog.render(open).get());
    }
}
