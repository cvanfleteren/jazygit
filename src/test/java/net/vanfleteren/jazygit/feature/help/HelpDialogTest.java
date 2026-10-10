package net.vanfleteren.jazygit.feature.help;

import static dev.tamboui.toolkit.Toolkit.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.RenderedText;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The popup listing the keys of a panel: Escape, Enter or ? closes it.
 */
class HelpDialogTest {

    private final List<Msg> dispatched = new ArrayList<>();
    private final HelpDialog dialog = new HelpDialog(dispatched::add);

    private static Model open(HelpTopic topic) {
        return Update.update(TestModels.loaded(), new HelpMsg.Requested(topic)).model();
    }

    @Test
    void listsTheKeysOfTheFilesPanel() throws Exception {
        String screen = screen(HelpTopic.FILES);
        assertTrue(screen.contains("Keybindings: Files"), screen);
        assertTrue(screen.contains("       space Stage/unstage"), screen);
        assertTrue(screen.contains("           c Commit"), screen);
        assertTrue(screen.contains("           A Amend last commit"), screen);
    }

    @Test
    void listsTheKeysOfTheBranchesPanel() throws Exception {
        String screen = screen(HelpTopic.BRANCHES);
        assertTrue(screen.contains("       space Checkout branch"), screen);
        assertTrue(screen.contains("           n New branch"), screen);
        assertTrue(screen.contains("           d Delete branch"), screen);
    }

    @Test
    void listsTheKeysOfTheCommitsPanel() throws Exception {
        assertTrue(screen(HelpTopic.COMMITS).contains("           r Reword last commit"));
    }

    @Test
    void escapeEnterAndQuestionMarkClose() throws Exception {
        assertEquals(List.of(new HelpMsg.Closed()), press(r -> r.pilot().press(KeyCode.ESCAPE)));
        assertEquals(List.of(new HelpMsg.Closed()), press(r -> r.pilot().press(KeyCode.ENTER)));
        assertEquals(List.of(new HelpMsg.Closed()), press(r -> r.pilot().press('?')));
    }

    @Test
    void closedModelShowsNoPopup() {
        assertTrue(dialog.render(TestModels.loaded()).isEmpty());
    }

    private List<Msg> press(java.util.function.Consumer<ToolkitTestRunner> keys) throws Exception {
        dispatched.clear();
        Model open = open(HelpTopic.FILES);
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(open))) {
            runner.runner().focusManager().setFocus(HelpDialog.ID);
            RenderedText.of(runner, () -> view(open));
            keys.accept(runner);
            runner.pilot().pause();
        }
        return List.copyOf(dispatched);
    }

    private String screen(HelpTopic topic) throws Exception {
        Model open = open(topic);
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(open))) {
            return RenderedText.ofLarge(runner, () -> view(open));
        }
    }

    private Element view(Model model) {
        return stack(text("background"), dialog.render(model).orElseThrow());
    }
}
