package net.vanfleteren.jazygit.feature.settings;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.Popup;

/**
 * The settings, while the user looks at them, with how gh is set up once that is read.
 *
 * @param theme        the theme in use
 * @param ghExecutable the gh executable chosen, if not the one on the PATH
 * @param gitHub       how gh is set up for the repository; none while it is read
 */
public record SettingsPanelPopup(ThemeName theme, Option<String> ghExecutable, Option<GitHubSetup> gitHub)
        implements Popup {}
