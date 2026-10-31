package net.vanfleteren.jazygit.feature.theme;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.ArrayList;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.CursorFeedback;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog;
import org.junit.jupiter.api.Test;

/**
 * The popup asking how to rebase the checked out branch.
 */
class ThemeDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final CursorFeedback cursor = new CursorFeedback(dispatched::add);
    private final ChoiceDialog<ThemePopup> dialog =
            new net.vanfleteren.jazygit.ui.widgets.ChoiceDialog<>(ThemeDialog.SPEC, cursor);
    private final Model open = Update.update(TestModels.loaded(new SampleData()), new ThemeMsg.Requested())
            .model();

    @Test
    void lettersChooseATheme() throws Exception {
        assertEquals(
                List.of(new ThemeMsg.Chosen(ThemeName.DARK)),
                pressing(r -> SettledPilot.of(r).press('d')));
        dispatched.clear();
        assertEquals(
                List.of(new ThemeMsg.Chosen(ThemeName.LIGHT)),
                pressing(r -> SettledPilot.of(r).press('l')));
        dispatched.clear();
        assertEquals(
                List.of(new ThemeMsg.Chosen(ThemeName.DARCULA)),
                pressing(r -> SettledPilot.of(r).press('a')));
        dispatched.clear();
        assertEquals(
                List.of(new ThemeMsg.Chosen(ThemeName.DARK_COLORBLIND)),
                pressing(r -> SettledPilot.of(r).press('b')));
        dispatched.clear();
        assertEquals(
                List.of(new ThemeMsg.Chosen(ThemeName.MONO)),
                pressing(r -> SettledPilot.of(r).press('m')));
        dispatched.clear();
        assertEquals(
                List.of(new ThemeMsg.Chosen(ThemeName.EMBER)),
                pressing(r -> SettledPilot.of(r).press('e')));
        dispatched.clear();
        assertEquals(
                List.of(new ThemeMsg.Chosen(ThemeName.SYMFONIC)),
                pressing(r -> SettledPilot.of(r).press('s')));
        dispatched.clear();
        assertEquals(
                List.of(new ThemeMsg.Chosen(ThemeName.GITHUB_DARK)),
                pressing(r -> SettledPilot.of(r).press('g')));
    }

    @Test
    void everyThemeCanBeChosen() {
        assertEquals(
                List.of(ThemeName.values()).map(ThemeMsg.Chosen::new).toSet(),
                ThemeDialog.SPEC.choices().map(choice -> choice.msg().get()).toSet());
    }

    @Test
    void arrowsAndEnterChooseTheSelectedLine() throws Exception {
        assertEquals(
                List.of(new ThemeMsg.Previewed(ThemeName.LIGHT), new ThemeMsg.Chosen(ThemeName.LIGHT)), pressing(r -> {
                    SettledPilot.of(r).press(KeyCode.DOWN);
                    SettledPilot.of(r).press(KeyCode.ENTER);
                }));
    }

    @Test
    void highlightingAThemePreviewsIt() throws Exception {
        assertEquals(
                List.of(
                        new ThemeMsg.Previewed(ThemeName.LIGHT),
                        new ThemeMsg.Previewed(ThemeName.DARCULA),
                        new ThemeMsg.Previewed(ThemeName.LIGHT)),
                pressing(r -> {
                    SettledPilot.of(r).press(KeyCode.DOWN);
                    SettledPilot.of(r).press(KeyCode.DOWN);
                    SettledPilot.of(r).press(KeyCode.UP);
                }));
    }

    @Test
    void opensAtTheThemeInUse() throws Exception {
        Model light = Update.update(
                        TestModels.loaded(new SampleData())
                                .mapPreferences(
                                        preferences -> preferences.withThemeOverride(Option.of(ThemeName.LIGHT))),
                        new ThemeMsg.Requested())
                .model();

        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(light))) {
            SettledPilot.focus(runner, ThemeDialog.SPEC.id());
            RenderedText.of(runner, () -> view(light));
            SettledPilot.of(runner).press(KeyCode.DOWN);
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(
                List.of(new ThemeMsg.Previewed(ThemeName.DARCULA), new ThemeMsg.Chosen(ThemeName.DARCULA)),
                List.ofAll(dispatched));
    }

    @Test
    void escapeCancels() throws Exception {
        assertEquals(
                List.of(new ThemeMsg.Cancelled()),
                pressing(r -> SettledPilot.of(r).press(KeyCode.ESCAPE)));
    }

    @Test
    void listsTheThemesAndMarksTheCurrentOne() throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view())) {
            String screen = RenderedText.ofLarge(runner, () -> view());
            assertTrue(screen.contains("(d) dark (current)"), screen);
            assertTrue(screen.contains("(l) light"), screen);
            assertTrue(screen.contains("(a) darcula"), screen);
            assertTrue(screen.contains("(b) dark, colour-blind friendly"), screen);
            assertTrue(screen.contains("(s) symfonic, orange and blue on black"), screen);
            assertTrue(screen.contains("(g) github (dark)"), screen);
        }
    }

    private List<Msg> pressing(Keys keys) throws Exception {
        try (ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view())) {
            SettledPilot.focus(runner, ThemeDialog.SPEC.id());
            RenderedText.of(runner, () -> view());
            keys.press(runner);
            SettledPilot.of(runner).pause();
        }
        return List.ofAll(dispatched);
    }

    private Element view() {
        return view(open);
    }

    private Element view(Model model) {
        return stack(text("background"), dialog.render(cursor.on(model)).get());
    }

    @FunctionalInterface
    private interface Keys {
        void press(ToolkitTestRunner runner) throws Exception;
    }
}
