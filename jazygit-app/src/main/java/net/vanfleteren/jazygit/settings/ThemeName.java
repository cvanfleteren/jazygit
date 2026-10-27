package net.vanfleteren.jazygit.settings;

import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * The themes the UI can be drawn in, in the order they are offered.
 */
public enum ThemeName {
    DARK("dark"),
    LIGHT("light"),
    DARCULA("darcula"),
    /**
     * The dark theme with blue and vermillion for red and green, for red/green colour blindness.
     */
    DARK_COLORBLIND("dark-colorblind"),
    /**
     * Mostly black and white, with muted colours only where they tell things apart.
     */
    MONO("mono"),
    /**
     * Saturated blues and oranges on deep navy.
     */
    EMBER("ember"),
    /**
     * The green, teal, blue and yellow of powerlevel10k's prompt segments on near-black.
     */
    POWERLINE("powerline"),
    /**
     * The purples, lavenders and cyan of Liantis on white.
     */
    LIANTIS("liantis"),
    /**
     * The liantis theme turned over: white and lavender on the deep purple of Liantis.
     */
    LIANTIS_DARK("liantis-dark"),
    /**
     * No styling at all: the terminal's own colours, as the UI is drawn without a theme.
     */
    NONE("none");

    private final String id;

    ThemeName(String id) {
        this.id = id;
    }

    /**
     * The theme named {@code id}, if there is one.
     */
    public static Option<ThemeName> of(String id) {
        return List.of(values()).find(theme -> theme.id.equals(id));
    }

    /**
     * How the theme is named in the settings file, and its stylesheet.
     */
    public String id() {
        return id;
    }
}
