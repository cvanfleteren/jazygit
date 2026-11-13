package net.vanfleteren.jazygit.settings;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.util.function.Function;
import net.vanfleteren.jazygit.lens.Lens;

/**
 * One of the settings, by its key in the settings file: where it is in a {@link Settings}, how the file's value is
 * read into it, and how it is kept in the file. A read of the file applies the settings one by one, as they changed
 * in it (ADR 111).
 *
 * <p>Adding a setting is a component of {@link Settings}, its default in {@link Settings#DEFAULT}, and a line in
 * {@link #ALL}.
 *
 * @param key     its key in the settings file, as {@link Settings#read} reports it when it is not valid
 * @param lens    where it is in some settings
 * @param decode  what the file's value holds of it, with the keys of what is wrong in that value
 * @param entries how it is kept in the settings file, by the path of each key; none when it is not kept there
 * @param <T>     what it is
 */
record Setting<T>(
        String key,
        Lens<Settings, T> lens,
        Function<Object, Decoded<T>> decode,
        Function<T, Map<List<String>, Object>> entries) {

    /**
     * Every setting the settings file holds, in the order a new file has them.
     */
    static final List<Setting<?>> ALL = List.of(
            // The theme is written even at its default.
            of(
                    Settings.THEME,
                    Lens.of(Settings::theme, Settings::withTheme),
                    value -> text(value).flatMap(ThemeName::of),
                    always(Settings.THEME, ThemeName::id)),
            of(
                    Settings.GH_EXECUTABLE,
                    Lens.of(Settings::ghExecutable, Settings::withGhExecutable),
                    value -> text(value).filter(path -> !path.isBlank()).map(Option::some),
                    path -> path.map(set -> entry(Settings.GH_EXECUTABLE, set)).getOrElse(LinkedHashMap.empty())),
            of(
                    Settings.REPOSITORY_WIDTH,
                    Lens.of(Settings::repositoryWidth, Settings::withRepositoryWidth),
                    Settings::width,
                    unlessDefault(Settings.REPOSITORY_WIDTH, Settings.DEFAULT_WIDTH, width -> width)),
            of(
                    Settings.PULL_REQUESTS_WIDTH,
                    Lens.of(Settings::pullRequestsWidth, Settings::withPullRequestsWidth),
                    Settings::width,
                    unlessDefault(Settings.PULL_REQUESTS_WIDTH, Settings.DEFAULT_WIDTH, width -> width)),
            of(
                    Settings.DIFF_STYLE,
                    Lens.of(Settings::diffStyle, Settings::withDiffStyle),
                    value -> text(value).flatMap(DiffStyle::of),
                    unlessDefault(Settings.DIFF_STYLE, Settings.DEFAULT.diffStyle(), DiffStyle::id)),
            of(
                    Settings.DIFF_SYNTAX,
                    Lens.of(Settings::diffSyntax, Settings::withDiffSyntax),
                    Setting::bool,
                    unlessDefault(Settings.DIFF_SYNTAX, Settings.DEFAULT.diffSyntax(), on -> on)),
            of(
                    Settings.DIFF_WORDS,
                    Lens.of(Settings::diffWords, Settings::withDiffWords),
                    Setting::bool,
                    unlessDefault(Settings.DIFF_WORDS, Settings.DEFAULT.diffWords(), on -> on)),
            // The TOML writer writes a java.util list as an array.
            of(
                    Settings.REPOSITORY_HEIGHTS,
                    Lens.of(Settings::repositoryHeights, Settings::withRepositoryHeights),
                    value -> Settings.heights(value, Settings.DEFAULT_REPOSITORY_HEIGHTS.size()),
                    unlessDefault(Settings.REPOSITORY_HEIGHTS, Settings.DEFAULT_REPOSITORY_HEIGHTS, List::toJavaList)),
            of(
                    Settings.PULL_REQUESTS_HEIGHTS,
                    Lens.of(Settings::pullRequestsHeights, Settings::withPullRequestsHeights),
                    value -> Settings.heights(value, Settings.DEFAULT_PULL_REQUESTS_HEIGHTS.size()),
                    unlessDefault(
                            Settings.PULL_REQUESTS_HEIGHTS, Settings.DEFAULT_PULL_REQUESTS_HEIGHTS, List::toJavaList)),
            of(
                    Settings.ANIMATIONS,
                    Lens.of(Settings::animations, Settings::withAnimations),
                    Setting::bool,
                    unlessDefault(Settings.ANIMATIONS, Settings.DEFAULT.animations(), on -> on)),
            // The browsers are a table of tables, by host and then by login.
            of(
                    Settings.BROWSERS,
                    Lens.of(Settings::browsers, Settings::withBrowsers),
                    AccountBrowsers::read,
                    AccountBrowsers::entries),
            // An entry of the editors that is wrong is left out, and reported; the others still apply. Only editors
            // that are no array at all are not valid as a whole. The user writes them, and saving leaves them as
            // they are.
            new Setting<>(
                    Editors.KEY,
                    Lens.of(Settings::editors, Settings::withEditors),
                    value -> {
                        Editors.Read read = Editors.read(value);
                        return new Decoded<>(Option.some(read.editors()), read.invalid());
                    },
                    editors -> LinkedHashMap.empty()));

    /**
     * A setting whose value in the file is valid as a whole or not at all: {@code valid} is what it holds, if it is
     * valid.
     */
    static <T> Setting<T> of(
            String key,
            Lens<Settings, T> lens,
            Function<Object, Option<T>> valid,
            Function<T, Map<List<String>, Object>> entries) {
        return new Setting<>(
                key,
                lens,
                value -> {
                    Option<T> read = valid.apply(value);
                    return new Decoded<>(read, Option.when(read.isEmpty(), key).toList());
                },
                entries);
    }

    /**
     * {@code read} with this setting as {@code value} finds it in the file, and with the keys of what is wrong in it;
     * as it was when {@code value} finds nothing.
     */
    Settings.Read readInto(Settings.Read read, Function<String, Option<Object>> value) {
        return value.apply(key)
                .map(decode)
                .map(decoded -> new Settings.Read(
                        decoded.value()
                                .map(found -> lens.map(read.settings(), held -> found))
                                .getOrElse(read.settings()),
                        read.invalid().appendAll(decoded.invalid())))
                .getOrElse(read);
    }

    /**
     * How {@code settings} keep this setting in the settings file.
     */
    Map<List<String>, Object> entriesOf(Settings settings) {
        return lens.get(settings).map(entries).getOrElse(LinkedHashMap.empty());
    }

    /**
     * {@code to}, with this setting as {@code from} has it.
     */
    Settings take(Settings to, Settings from) {
        return lens.get(from).map(value -> lens.map(to, held -> value)).getOrElse(to);
    }

    /**
     * Whether {@code first} and {@code second} differ in it.
     */
    boolean differs(Settings first, Settings second) {
        return !lens.get(first).equals(lens.get(second));
    }

    /**
     * The setting kept at {@code key} as {@code written}, whatever it is.
     */
    private static <T> Function<T, Map<List<String>, Object>> always(String key, Function<T, Object> written) {
        return value -> entry(key, written.apply(value));
    }

    /**
     * The setting kept at {@code key} as {@code written}, but not when it is {@code standard}, its default.
     */
    private static <T> Function<T, Map<List<String>, Object>> unlessDefault(
            String key, T standard, Function<T, Object> written) {
        return value -> value.equals(standard) ? LinkedHashMap.empty() : entry(key, written.apply(value));
    }

    private static Map<List<String>, Object> entry(String key, Object value) {
        return LinkedHashMap.of(List.of(key.split("\\.")), value);
    }

    private static Option<String> text(Object value) {
        return Option.of(value).filter(String.class::isInstance).map(String.class::cast);
    }

    private static Option<Boolean> bool(Object value) {
        return Option.of(value).filter(Boolean.class::isInstance).map(Boolean.class::cast);
    }

    /**
     * What the file's value holds of a setting, if anything, with the keys of what is wrong in it.
     */
    record Decoded<T>(Option<T> value, List<String> invalid) {}
}
