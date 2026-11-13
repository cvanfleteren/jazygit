package net.vanfleteren.jazygit.settings;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.util.function.BiFunction;
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
 * @param key           its key in the settings file, as {@link Settings#read} reports it when it is not valid
 * @param lens          where it is in some settings
 * @param decode        what the file's value holds of it, with the keys of what is wrong in that value
 * @param entries       how it is kept in the settings file at its key, by the path of each key; none when it is not
 *                      kept there
 * @param keptAtDefault whether it is kept in the file at its default too; otherwise it has no entry then
 * @param <T>           what it is
 */
record Setting<T>(
        String key,
        Lens<Settings, T> lens,
        Function<Object, Decoded<T>> decode,
        BiFunction<String, T, Map<List<String>, Object>> entries,
        boolean keptAtDefault) {

    /**
     * Every setting the settings file holds, in the order a new file has them.
     */
    static final List<Setting<?>> ALL = List.of(
            of(
                            Settings.THEME,
                            Lens.of(Settings::theme, Settings::withTheme),
                            value -> text(value).flatMap(ThemeName::of),
                            as(ThemeName::id))
                    .alsoAtDefault(),
            of(
                    Settings.GH_EXECUTABLE,
                    Lens.of(Settings::ghExecutable, Settings::withGhExecutable),
                    value -> text(value).filter(path -> !path.isBlank()).map(Option::some),
                    (key, path) -> path.map(set -> entry(key, set)).getOrElse(LinkedHashMap.empty())),
            of(
                    Settings.REPOSITORY_WIDTH,
                    Lens.of(Settings::repositoryWidth, Settings::withRepositoryWidth),
                    Settings::width,
                    as(width -> width)),
            of(
                    Settings.PULL_REQUESTS_WIDTH,
                    Lens.of(Settings::pullRequestsWidth, Settings::withPullRequestsWidth),
                    Settings::width,
                    as(width -> width)),
            of(
                    Settings.DIFF_STYLE,
                    Lens.of(Settings::diffStyle, Settings::withDiffStyle),
                    value -> text(value).flatMap(DiffStyle::of),
                    as(DiffStyle::id)),
            of(
                    Settings.DIFF_SYNTAX,
                    Lens.of(Settings::diffSyntax, Settings::withDiffSyntax),
                    Setting::bool,
                    as(on -> on)),
            of(Settings.DIFF_WORDS, Lens.of(Settings::diffWords, Settings::withDiffWords), Setting::bool, as(on -> on)),
            // The TOML writer writes a java.util list as an array.
            of(
                    Settings.REPOSITORY_HEIGHTS,
                    Lens.of(Settings::repositoryHeights, Settings::withRepositoryHeights),
                    value -> Settings.heights(value, Settings.DEFAULT_REPOSITORY_HEIGHTS.size()),
                    as(List::toJavaList)),
            of(
                    Settings.PULL_REQUESTS_HEIGHTS,
                    Lens.of(Settings::pullRequestsHeights, Settings::withPullRequestsHeights),
                    value -> Settings.heights(value, Settings.DEFAULT_PULL_REQUESTS_HEIGHTS.size()),
                    as(List::toJavaList)),
            of(
                    Settings.ANIMATIONS,
                    Lens.of(Settings::animations, Settings::withAnimations),
                    Setting::bool,
                    as(on -> on)),
            // The browsers are a table of tables, by host and then by login.
            of(
                    Settings.BROWSERS,
                    Lens.of(Settings::browsers, Settings::withBrowsers),
                    AccountBrowsers::read,
                    (key, browsers) -> browsers.entries()),
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
                    (key, editors) -> LinkedHashMap.empty(),
                    false));

    /**
     * A setting whose value in the file is valid as a whole or not at all: {@code valid} is what it holds, if it is
     * valid. It has no entry in the file at its default.
     */
    static <T> Setting<T> of(
            String key,
            Lens<Settings, T> lens,
            Function<Object, Option<T>> valid,
            BiFunction<String, T, Map<List<String>, Object>> entries) {
        return new Setting<>(
                key,
                lens,
                value -> {
                    Option<T> read = valid.apply(value);
                    return new Decoded<>(read, Option.when(read.isEmpty(), key).toList());
                },
                entries,
                false);
    }

    /**
     * This setting, kept in the file at its default too.
     */
    Setting<T> alsoAtDefault() {
        return new Setting<>(key, lens, decode, entries, true);
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
     * How {@code settings} keep this setting in the settings file: nothing when it is at its default, as
     * {@link Settings#DEFAULT} has it, unless it is kept there too.
     */
    Map<List<String>, Object> entriesOf(Settings settings) {
        return lens.get(settings)
                .filter(value -> keptAtDefault || !lens.get(Settings.DEFAULT).contains(value))
                .map(value -> entries.apply(key, value))
                .getOrElse(LinkedHashMap.empty());
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
     * The setting kept at its key as {@code written}.
     */
    private static <T> BiFunction<String, T, Map<List<String>, Object>> as(Function<T, Object> written) {
        return (key, value) -> entry(key, written.apply(value));
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
