package net.vanfleteren.jazygit.feature.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.Theme;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class ThemeUpdateTest {

    @Test
    void requestOpensTheChoiceWithTheCurrentTheme() {
        Model model = Update.update(TestModels.loaded(), new ThemeMsg.Requested()).model();

        assertEquals(Optional.of(new ThemePopup(Theme.DARK)), model.popup(ThemePopup.class));
    }

    @Test
    void choosingAThemeClosesThePopupAndSwitches() {
        Model open = Update.update(TestModels.loaded(), new ThemeMsg.Requested()).model();

        Model chosen = Update.update(open, new ThemeMsg.Chosen(Theme.DARCULA)).model();

        assertEquals(Optional.empty(), chosen.popup(ThemePopup.class));
        assertEquals(Theme.DARCULA, chosen.theme());
    }

    @Test
    void cancellingKeepsTheTheme() {
        Model open = Update.update(TestModels.loaded(), new ThemeMsg.Requested()).model();

        Model cancelled = Update.update(open, new ThemeMsg.Cancelled()).model();

        assertEquals(Optional.empty(), cancelled.popup(ThemePopup.class));
        assertEquals(Theme.DARK, cancelled.theme());
    }
}
