package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.settings.ThemeName;

/**
 * How the user wants things shown: what is kept from one session to the next, and what holds for this one only.
 *
 * @param settings      what the user chose to keep from one session to the next, like the theme
 * @param themeOverride the theme asked for at startup, or highlighted in the theme dialog, for this session only,
 *                      until the user chooses one
 * @param diffOptions   how the diffs of the files and the commits are read, for this session only
 */
@With
public record Preferences(Settings settings, Option<ThemeName> themeOverride, DiffOptions diffOptions) {

    public static final Preferences DEFAULT = new Preferences(Settings.DEFAULT, Option.none(), DiffOptions.DEFAULT);

    /**
     * The theme the UI is drawn in: the one asked for at startup, else the saved one.
     */
    public ThemeName theme() {
        return themeOverride.getOrElse(settings.theme());
    }
}
