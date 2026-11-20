package net.vanfleteren.jazygit.features.chrome.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.kernel.settings.Settings;
import net.vanfleteren.jazygit.kernel.settings.ThemeName;
import net.vanfleteren.jazygit.kernel.state.preferences.RepositoryTheme;
import net.vanfleteren.jazygit.kernel.state.settings.SettingsPanelPopup;
import net.vanfleteren.jazygit.kernel.ui.settings.SettingsSection;
import net.vanfleteren.jazygit.kernel.ui.widgets.ChoiceDialog.Choice;
import org.junit.jupiter.api.Test;

/**
 * The panel's own sections; the panel with the sections of the features is drawn in the app's tests.
 */
class SettingsPanelDialogsTest {

    private static final SettingsPanelPopup POPUP = new SettingsPanelPopup(Settings.DEFAULT);

    @Test
    void theSettingsFileShowsWhereItIsWithATildeForTheHomeDirectory() {
        Path home = Path.of(System.getProperty("user.home"));

        assertEquals(
                "Edit ~/.config/jazygit/config.toml",
                SettingsPanelDialogs.file(POPUP.with(
                        SettingsPanelUpdate.FILE,
                        Option.of(home.resolve(".config/jazygit/config.toml").toString()))));
        assertEquals(
                "Edit /etc/jazygit/config.toml",
                SettingsPanelDialogs.file(POPUP.with(SettingsPanelUpdate.FILE, Option.of("/etc/jazygit/config.toml"))));
        assertEquals("Edit reading…", SettingsPanelDialogs.file(POPUP));
    }

    @Test
    void theThemeOfTheRepositoryIsItsOwnElseTheOneItInherits() {
        RepositoryTheme.Inherited work = new RepositoryTheme.Inherited(ThemeName.LIGHT, "/etc/gitconfig-work");

        assertEquals(
                "Theme for this repository: ember, blues and oranges",
                SettingsPanelDialogs.repositoryTheme(POPUP.with(
                        SettingsPanelUpdate.REPOSITORY_THEME,
                        new RepositoryTheme(Option.of(ThemeName.EMBER), Option.of(work)))));
        assertEquals(
                "Theme for this repository: light, from /etc/gitconfig-work",
                SettingsPanelDialogs.repositoryTheme(POPUP.with(
                        SettingsPanelUpdate.REPOSITORY_THEME, new RepositoryTheme(Option.none(), Option.of(work)))));
        assertEquals(
                "Theme for this repository: none, the one for all repositories",
                SettingsPanelDialogs.repositoryTheme(POPUP));
    }

    @Test
    void theSectionsOfTheFeaturesComeAfterThePanelsOwnAndBeforeTheSettingsFile() {
        SettingsSection added = SettingsSection.of(
                "chrome.dialog.settings.group.git",
                new Choice<SettingsPanelPopup>('x', popup -> "x", SettingsPanelMsg.FileRequested::new));

        assertEquals(
                List.of('t', 'r', 'm', 's', 'f', 'x', 'e'),
                SettingsPanelDialogs.panel(List.of(added)).choices().map(Choice::key));
        assertEquals(
                List.of(
                        "chrome.dialog.settings.group.appearance",
                        "chrome.dialog.settings.group.git",
                        "chrome.dialog.settings.group.git",
                        "chrome.dialog.settings.group.file"),
                SettingsPanelDialogs.panel(List.of(added)).choices().flatMap(Choice::heading));
    }
}
