package net.vanfleteren.jazygit.feature.settings;

import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.settings.AccountBrowsers;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.popup.Popup;
import net.vanfleteren.jazygit.state.preferences.RepositoryTheme;

/**
 * The settings, while the user looks at them, with how gh is set up once that is read.
 *
 * @param theme           the theme kept for every repository the git configuration gives none
 * @param repositoryTheme the theme the git configuration gives the repository (ADR 70)
 * @param ghExecutable    the gh executable chosen, if not the one on the PATH
 * @param gitHub          how gh is set up for the repository; none while it is read
 * @param animations      whether what moves on the screen moves there smoothly (ADR 81)
 * @param browsers        the browser set for each GitHub account (ADR 88)
 * @param file            the settings file, which {@code e} opens; none while it is read, or when the settings are
 *                        kept in no file (ADR 111)
 * @param opensAt         the key of the setting highlighted when the panel opens, like the one a dialog was opened
 *                        from that cancelling goes back from; else the first
 */
@With
public record SettingsPanelPopup(
        ThemeName theme,
        RepositoryTheme repositoryTheme,
        Option<String> ghExecutable,
        Option<GitHubSetup> gitHub,
        boolean animations,
        AccountBrowsers browsers,
        Option<String> file,
        Option<Character> opensAt)
        implements Popup {

    /**
     * The settings, opening with the first one highlighted.
     */
    public SettingsPanelPopup(
            ThemeName theme,
            RepositoryTheme repositoryTheme,
            Option<String> ghExecutable,
            Option<GitHubSetup> gitHub,
            boolean animations,
            AccountBrowsers browsers,
            Option<String> file) {
        this(theme, repositoryTheme, ghExecutable, gitHub, animations, browsers, file, Option.none());
    }
}
