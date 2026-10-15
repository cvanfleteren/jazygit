package net.vanfleteren.jazygit.feature.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;
import org.junit.jupiter.api.Test;

class ThemeUpdateTest {

    @Test
    void requestOpensTheChoiceWithTheCurrentTheme() {
        Model model = Update.update(TestModels.loaded(), new ThemeMsg.Requested()).model();

        assertEquals(Option.of(new ThemePopup(ThemeName.DARK)), model.popup(ThemePopup.class));
    }

    @Test
    void requestWhileADialogIsOpenLeavesThatDialogOpen() {
        Model typing = TestModels.loaded().openPopup(new net.vanfleteren.jazygit.feature.commit.CommitPopup());

        Model model = Update.update(typing, new ThemeMsg.Requested()).model();

        assertEquals(typing.popup(), model.popup());
    }

    @Test
    void choosingAThemeClosesThePopupSwitchesAndSavesIt() {
        Model open = Update.update(TestModels.loaded(), new ThemeMsg.Requested()).model();

        Next chosen = Update.update(open, new ThemeMsg.Chosen(ThemeName.DARCULA));

        assertEquals(Option.none(), chosen.model().popup(ThemePopup.class));
        assertEquals(ThemeName.DARCULA, chosen.model().settings().theme());
        assertEquals(List.of(new Cmd.SaveSettings(Settings.DEFAULT, new Settings(ThemeName.DARCULA))), chosen.cmds());
    }

    @Test
    void choosingTheCurrentThemeSavesNothing() {
        Model open = Update.update(TestModels.loaded(), new ThemeMsg.Requested()).model();

        Next chosen = Update.update(open, new ThemeMsg.Chosen(ThemeName.DARK));

        assertEquals(Option.none(), chosen.model().popup(ThemePopup.class));
        assertEquals(List.empty(), chosen.cmds());
    }

    @Test
    void requestOpensTheChoiceWithTheThemeAskedForAtStartup() {
        Model model = Update.update(TestModels.loaded().withThemeOverride(Option.of(ThemeName.LIGHT)),
                new ThemeMsg.Requested()).model();

        assertEquals(Option.of(new ThemePopup(ThemeName.LIGHT)), model.popup(ThemePopup.class));
    }

    @Test
    void choosingTheThemeAskedForAtStartupSavesIt() {
        Model open = Update.update(TestModels.loaded().withThemeOverride(Option.of(ThemeName.LIGHT)),
                new ThemeMsg.Requested()).model();

        Next chosen = Update.update(open, new ThemeMsg.Chosen(ThemeName.LIGHT));

        assertEquals(Option.none(), chosen.model().themeOverride());
        assertEquals(ThemeName.LIGHT, chosen.model().theme());
        assertEquals(List.of(new Cmd.SaveSettings(Settings.DEFAULT, new Settings(ThemeName.LIGHT))), chosen.cmds());
    }

    @Test
    void choosingTheSavedThemeOverTheOneAskedForAtStartupSwitchesBackAndSavesNothing() {
        Model open = Update.update(TestModels.loaded().withThemeOverride(Option.of(ThemeName.LIGHT)),
                new ThemeMsg.Requested()).model();

        Next chosen = Update.update(open, new ThemeMsg.Chosen(ThemeName.DARK));

        assertEquals(ThemeName.DARK, chosen.model().theme());
        assertEquals(List.empty(), chosen.cmds());
    }

    @Test
    void cancellingKeepsTheThemeAskedForAtStartup() {
        Model open = Update.update(TestModels.loaded().withThemeOverride(Option.of(ThemeName.LIGHT)),
                new ThemeMsg.Requested()).model();

        Next cancelled = Update.update(open, new ThemeMsg.Cancelled());

        assertEquals(ThemeName.LIGHT, cancelled.model().theme());
        assertEquals(List.empty(), cancelled.cmds());
    }

    @Test
    void cancellingKeepsTheTheme() {
        Model open = Update.update(TestModels.loaded(), new ThemeMsg.Requested()).model();

        Next cancelled = Update.update(open, new ThemeMsg.Cancelled());

        assertEquals(Option.none(), cancelled.model().popup(ThemePopup.class));
        assertEquals(ThemeName.DARK, cancelled.model().settings().theme());
        assertEquals(List.empty(), cancelled.cmds());
    }
}
