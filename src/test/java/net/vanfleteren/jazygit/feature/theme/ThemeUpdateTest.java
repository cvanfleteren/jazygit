package net.vanfleteren.jazygit.feature.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.Theme;
import org.junit.jupiter.api.Test;

class ThemeUpdateTest {

    @Test
    void requestOpensTheChoiceWithTheCurrentTheme() {
        Model model = Update.update(TestModels.loaded(), new ThemeMsg.Requested()).model();

        assertEquals(Option.of(new ThemePopup(Theme.DARK)), model.popup(ThemePopup.class));
    }

    @Test
    void requestWhileADialogIsOpenLeavesThatDialogOpen() {
        Model typing = TestModels.loaded().openPopup(new net.vanfleteren.jazygit.feature.commit.CommitPopup());

        Model model = Update.update(typing, new ThemeMsg.Requested()).model();

        assertEquals(typing.popup(), model.popup());
    }

    @Test
    void choosingAThemeClosesThePopupAndSwitches() {
        Model open = Update.update(TestModels.loaded(), new ThemeMsg.Requested()).model();

        Model chosen = Update.update(open, new ThemeMsg.Chosen(Theme.DARCULA)).model();

        assertEquals(Option.none(), chosen.popup(ThemePopup.class));
        assertEquals(Theme.DARCULA, chosen.theme());
    }

    @Test
    void cancellingKeepsTheTheme() {
        Model open = Update.update(TestModels.loaded(), new ThemeMsg.Requested()).model();

        Model cancelled = Update.update(open, new ThemeMsg.Cancelled()).model();

        assertEquals(Option.none(), cancelled.popup(ThemePopup.class));
        assertEquals(Theme.DARK, cancelled.theme());
    }
}
