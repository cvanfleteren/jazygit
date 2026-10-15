package net.vanfleteren.jazygit.settings;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.util.function.Function;
import lombok.With;

/**
 * What the user chose to keep from one session to the next.
 *
 * @param theme        the theme the UI is drawn in
 * @param ghExecutable the gh executable to run, if not the one on the PATH
 */
@With
public record Settings(ThemeName theme, Option<String> ghExecutable) {

    /**
     * The settings of a first session.
     */
    public static final Settings DEFAULT = new Settings(ThemeName.DARK, Option.none());

    /**
     * The key of the theme in the settings file.
     */
    static final String THEME = "ui.theme";
    /**
     * The key of the gh executable in the settings file.
     */
    static final String GH_EXECUTABLE = "github.executable";

    /**
     * The settings that {@code value} finds by their dotted key: the default for the ones it does not find, and
     * for the ones it finds no valid value for, whose keys come with them.
     */
    static Read read(Function<String, Option<Object>> value) {
        Option<Object> theme = value.apply(THEME);
        Option<ThemeName> validTheme =
                theme.flatMap(found -> found instanceof String id ? ThemeName.of(id) : Option.none());
        Option<Object> gh = value.apply(GH_EXECUTABLE);
        Option<String> validGh =
                gh.flatMap(found -> found instanceof String path && !path.isBlank() ? Option.of(path) : Option.none());
        return new Read(
                new Settings(validTheme.getOrElse(DEFAULT.theme), validGh),
                Option.when(theme.isDefined() && validTheme.isEmpty(), THEME)
                        .toList()
                        .appendAll(Option.when(gh.isDefined() && validGh.isEmpty(), GH_EXECUTABLE)));
    }

    /**
     * The gh executable to run: the one chosen, else the one on the PATH.
     */
    public String gh() {
        return ghExecutable.getOrElse("gh");
    }

    /**
     * The settings as they are kept in the settings file, by their dotted key; a setting left at its default
     * has none.
     */
    Map<String, Object> entries() {
        return LinkedHashMap.<String, Object>of(THEME, theme.id())
                .merge(ghExecutable
                        .map(path -> LinkedHashMap.<String, Object>of(GH_EXECUTABLE, path))
                        .getOrElse(LinkedHashMap.empty()));
    }

    /**
     * Settings as read, with the keys that had an invalid value.
     */
    record Read(Settings settings, List<String> invalid) {}
}
