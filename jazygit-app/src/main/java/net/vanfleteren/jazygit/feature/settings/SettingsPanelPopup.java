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
 */
@With
public record SettingsPanelPopup(
        ThemeName theme,
        RepositoryTheme repositoryTheme,
        Option<String> ghExecutable,
        Option<GitHubSetup> gitHub,
        boolean animations,
        AccountBrowsers browsers)
        implements Popup {}
