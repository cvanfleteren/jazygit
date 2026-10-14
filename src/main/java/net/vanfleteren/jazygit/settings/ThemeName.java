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
    /** The dark theme with blue and vermillion for red and green, for red/green colour blindness. */
    DARK_COLORBLIND("dark-colorblind");

    private final String id;

    ThemeName(String id) {
        this.id = id;
    }

    /**
     * How the theme is named in the settings file, and its stylesheet.
     */
    public String id() {
        return id;
    }

    /**
     * The theme named {@code id}, if there is one.
     */
    public static Option<ThemeName> of(String id) {
        return List.of(values()).find(theme -> theme.id.equals(id));
    }
}
