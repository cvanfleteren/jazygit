package net.vanfleteren.jazygit.settings;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import lombok.With;

import java.util.function.Function;

/**
 * What the user chose to keep from one session to the next.
 *
 * @param theme the theme the UI is drawn in
 */
@With
public record Settings(ThemeName theme) {

    /** The settings of a first session. */
    public static final Settings DEFAULT = new Settings(ThemeName.DARK);

    /** The key of the theme in the settings file. */
    static final String THEME = "ui.theme";

    /**
     * The settings as they are kept in the settings file, by their dotted key.
     */
    Map<String, Object> entries() {
        return LinkedHashMap.of(THEME, theme.id());
    }

    /**
     * The settings that {@code value} finds by their dotted key: the default for the ones it does not find, and
     * for the ones it finds no valid value for, whose keys come with them.
     */
    static Read read(Function<String, Option<Object>> value) {
        Option<Object> theme = value.apply(THEME);
        Option<ThemeName> valid = theme.flatMap(found -> found instanceof String id ? ThemeName.of(id) : Option.none());
        return new Read(new Settings(valid.getOrElse(DEFAULT.theme)),
                Option.when(theme.isDefined() && valid.isEmpty(), THEME).toList());
    }

    /**
     * Settings as read, with the keys that had an invalid value.
     */
    record Read(Settings settings, List<String> invalid) {
    }
}
