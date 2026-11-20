package net.vanfleteren.jazygit.kernel.settings;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.TreeMap;
import io.vavr.control.Option;
import java.util.function.Function;

/**
 * What the user chose to keep from one session to the next: the value of each setting, by the path of its
 * {@link SettingKey} (ADR 163). A setting not held has its initial value, and one set back to it is not held, so two
 * settings with the same values are equal, whoever declared the keys.
 *
 * <p>The kernel's own settings have an accessor and a wither each, which read and set their key.
 *
 * @param held each setting that differs from its initial value, with its value, by its path
 */
public record Settings(Map<String, Held<?>> held) {

    /**
     * The narrowest the left column may get, in percent of the width.
     */
    public static final int MIN_WIDTH = 15;
    /**
     * The widest the left column may get, in percent of the width.
     */
    public static final int MAX_WIDTH = 70;
    /**
     * How wide the left column is unless the user changed it, in percent of the width.
     */
    public static final int DEFAULT_WIDTH = 30;
    /**
     * The shortest a pane of the left column may get, in percent of its height.
     */
    public static final int MIN_HEIGHT = 10;
    /**
     * How tall the files, the branches and the commits are unless the user changed it, in percent of the height of
     * the left column; the stash takes what is left.
     */
    public static final List<Integer> DEFAULT_REPOSITORY_HEIGHTS = List.of(25, 20, 30);
    /**
     * How tall the first two pull requests panes are unless the user changed it, in percent of the height of the
     * left column; the last takes what is left, which is about as much in a column under the status.
     */
    public static final List<Integer> DEFAULT_PULL_REQUESTS_HEIGHTS = List.of(32, 32);

    /**
     * The settings of a first session: each at its initial value.
     */
    public static final Settings DEFAULT = new Settings(TreeMap.empty());

    /**
     * The theme the UI is drawn in. The file shows it at its initial value too.
     */
    public static final SettingKey<ThemeName> THEME = SettingKey.of(
                    "ui.theme", ThemeName.DARK, Codec.text(ThemeName::of, ThemeName::id))
            .withKept(Kept.ALWAYS);
    /**
     * The gh executable to run, if not the one on the PATH; one that is blank is not valid.
     */
    public static final SettingKey<Option<String>> GH_EXECUTABLE = SettingKey.of(
                    "github.executable",
                    Option.none(),
                    Codec.text(path -> Option.when(!path.isBlank(), path), path -> path)
                            .optional())
            // The GitHub client runs it, which a repository gets as it is shown.
            .withReopens(true);
    /**
     * The width of the left column of the repository screen.
     */
    public static final SettingKey<Integer> REPOSITORY_WIDTH =
            SettingKey.of("ui.repository-width", DEFAULT_WIDTH, Codec.whole(Settings::width, width -> width));
    /**
     * The width of the left column of the pull requests screen.
     */
    public static final SettingKey<Integer> PULL_REQUESTS_WIDTH =
            SettingKey.of("ui.pull-requests-width", DEFAULT_WIDTH, Codec.whole(Settings::width, width -> width));
    /**
     * The diff style; the default has become rich diffs.
     */
    public static final SettingKey<DiffStyle> DIFF_STYLE =
            SettingKey.of("ui.diff-style", DiffStyle.RICH, Codec.text(DiffStyle::of, DiffStyle::id));
    /**
     * Whether the rich view colours code by its syntax.
     */
    public static final SettingKey<Boolean> DIFF_SYNTAX = SettingKey.of("ui.diff-syntax", true, Codec.bool());
    /**
     * Whether the rich view highlights the changed words of a line.
     */
    public static final SettingKey<Boolean> DIFF_WORDS = SettingKey.of("ui.diff-words", true, Codec.bool());
    /**
     * The heights of the panes of the left column of the repository screen. The TOML writer writes a java.util list
     * as an array.
     */
    public static final SettingKey<List<Integer>> REPOSITORY_HEIGHTS = SettingKey.of(
            "ui.repository-heights",
            DEFAULT_REPOSITORY_HEIGHTS,
            Codec.whole(value -> heights(value, DEFAULT_REPOSITORY_HEIGHTS.size()), List::toJavaList));
    /**
     * The heights of the panes of the left column of the pull requests screen.
     */
    public static final SettingKey<List<Integer>> PULL_REQUESTS_HEIGHTS = SettingKey.of(
            "ui.pull-requests-heights",
            DEFAULT_PULL_REQUESTS_HEIGHTS,
            Codec.whole(value -> heights(value, DEFAULT_PULL_REQUESTS_HEIGHTS.size()), List::toJavaList));
    /**
     * Whether what moves on the screen moves there smoothly (ADR 81).
     */
    public static final SettingKey<Boolean> ANIMATIONS = SettingKey.of("ui.animations", true, Codec.bool());
    /**
     * Whether the repository is read again every so often (ADR 125).
     */
    public static final SettingKey<Boolean> AUTO_REFRESH = SettingKey.of("git.auto-refresh", true, Codec.bool());
    /**
     * Whether the remotes are fetched from every so often (ADR 125).
     */
    public static final SettingKey<Boolean> AUTO_FETCH = SettingKey.of("git.auto-fetch", true, Codec.bool());
    /**
     * The browser of each GitHub account (ADR 88): a table of tables, by host and then by login.
     */
    public static final SettingKey<AccountBrowsers> BROWSERS = SettingKey.of(
            "github.browsers",
            AccountBrowsers.NONE,
            Codec.of(
                    Codec.whole(AccountBrowsers::read, browsers -> browsers)::decode,
                    (key, browsers) -> browsers.entries()));
    /**
     * The editors (ADR 110). An entry that is wrong is left out, and reported; the others still apply. Only editors
     * that are no array at all are not valid as a whole.
     */
    public static final SettingKey<Editors> EDITORS =
            SettingKey.userOwned(Editors.KEY, Editors.NONE, Codec.readOnly(value -> {
                Editors.Read read = Editors.read(value);
                return new Decoded<>(Option.some(read.editors()), read.invalid());
            }));
    /**
     * The ids of the diff filters enabled (ADR 144), each once, in the order they were enabled.
     */
    public static final SettingKey<List<String>> DIFF_FILTERS =
            SettingKey.of("ui.diff-filters", List.empty(), Codec.whole(Settings::ids, List::toJavaList));
    /**
     * The diff filters the user set (ADR 144); as the editors.
     */
    public static final SettingKey<FilterDefinitions> FILTER_DEFINITIONS =
            SettingKey.userOwned(FilterDefinitions.KEY, FilterDefinitions.NONE, Codec.readOnly(value -> {
                FilterDefinitions.Read read = FilterDefinitions.read(value, false);
                return new Decoded<>(Option.some(read.filters()), read.invalid());
            }));
    /**
     * The value of {@code key}: its initial value while it is not held.
     */
    @SuppressWarnings("unchecked") // Only with() holds a value under a key's path, one of that key's type.
    public <T> T get(SettingKey<T> key) {
        return held.get(key.path()).map(value -> (T) value.value()).getOrElse(key.initial());
    }

    /**
     * These settings with {@code value} for {@code key}; not held when that is its initial value.
     */
    public <T> Settings with(SettingKey<T> key, T value) {
        return new Settings(
                value.equals(key.initial()) ? held.remove(key.path()) : held.put(key.path(), new Held<>(key, value)));
    }

    public ThemeName theme() {
        return get(THEME);
    }

    public Settings withTheme(ThemeName theme) {
        return with(THEME, theme);
    }

    public Option<String> ghExecutable() {
        return get(GH_EXECUTABLE);
    }

    public Settings withGhExecutable(Option<String> ghExecutable) {
        return with(GH_EXECUTABLE, ghExecutable);
    }

    public int repositoryWidth() {
        return get(REPOSITORY_WIDTH);
    }

    public Settings withRepositoryWidth(int width) {
        return with(REPOSITORY_WIDTH, width);
    }

    public int pullRequestsWidth() {
        return get(PULL_REQUESTS_WIDTH);
    }

    public Settings withPullRequestsWidth(int width) {
        return with(PULL_REQUESTS_WIDTH, width);
    }

    public DiffStyle diffStyle() {
        return get(DIFF_STYLE);
    }

    public Settings withDiffStyle(DiffStyle style) {
        return with(DIFF_STYLE, style);
    }

    public boolean diffSyntax() {
        return get(DIFF_SYNTAX);
    }

    public Settings withDiffSyntax(boolean on) {
        return with(DIFF_SYNTAX, on);
    }

    public boolean diffWords() {
        return get(DIFF_WORDS);
    }

    public Settings withDiffWords(boolean on) {
        return with(DIFF_WORDS, on);
    }

    public List<Integer> repositoryHeights() {
        return get(REPOSITORY_HEIGHTS);
    }

    public Settings withRepositoryHeights(List<Integer> heights) {
        return with(REPOSITORY_HEIGHTS, heights);
    }

    public List<Integer> pullRequestsHeights() {
        return get(PULL_REQUESTS_HEIGHTS);
    }

    public Settings withPullRequestsHeights(List<Integer> heights) {
        return with(PULL_REQUESTS_HEIGHTS, heights);
    }

    public boolean animations() {
        return get(ANIMATIONS);
    }

    public Settings withAnimations(boolean on) {
        return with(ANIMATIONS, on);
    }

    public boolean autoRefresh() {
        return get(AUTO_REFRESH);
    }

    public Settings withAutoRefresh(boolean on) {
        return with(AUTO_REFRESH, on);
    }

    public boolean autoFetch() {
        return get(AUTO_FETCH);
    }

    public Settings withAutoFetch(boolean on) {
        return with(AUTO_FETCH, on);
    }

    public AccountBrowsers browsers() {
        return get(BROWSERS);
    }

    public Settings withBrowsers(AccountBrowsers browsers) {
        return with(BROWSERS, browsers);
    }

    public Editors editors() {
        return get(EDITORS);
    }

    public Settings withEditors(Editors editors) {
        return with(EDITORS, editors);
    }

    public List<String> diffFilters() {
        return get(DIFF_FILTERS);
    }

    public Settings withDiffFilters(List<String> ids) {
        return with(DIFF_FILTERS, ids);
    }

    public FilterDefinitions filterDefinitions() {
        return get(FILTER_DEFINITIONS);
    }

    public Settings withFilterDefinitions(FilterDefinitions filters) {
        return with(FILTER_DEFINITIONS, filters);
    }

    /**
     * {@code width} brought within the widths the left column may have.
     */
    public static int clampWidth(int width) {
        return Math.clamp(width, MIN_WIDTH, MAX_WIDTH);
    }

    /**
     * {@code heights} with pane {@code pane} {@code height} percent tall, and the pane under it as much shorter or
     * taller, both kept at least {@link #MIN_HEIGHT}; the last pane takes what the others leave. Unchanged for a
     * pane that has no height of its own.
     */
    public static List<Integer> resizedHeights(List<Integer> heights, int pane, int height) {
        if (pane < 0 || pane >= heights.size()) {
            return heights;
        }
        int below = pane + 1 < heights.size()
                ? heights.get(pane + 1)
                : 100 - heights.sum().intValue();
        int both = heights.get(pane) + below;
        int resized = Math.clamp(height, MIN_HEIGHT, Math.max(MIN_HEIGHT, both - MIN_HEIGHT));
        List<Integer> above = heights.update(pane, resized);
        return pane + 1 < heights.size() ? above.update(pane + 1, both - resized) : above;
    }

    /**
     * The settings of {@code keys} that {@code value} finds by their dotted key: the initial value for the ones it
     * does not find, and for the ones it finds no valid value for, whose keys come with them.
     */
    static Read read(SettingKeys keys, Function<String, Option<Object>> value) {
        return keys.all().foldLeft(new Read(DEFAULT, List.empty()), (read, key) -> key.readInto(read, value));
    }

    /**
     * The width of the left column {@code value} is, if it is a whole number of percent the column may have.
     */
    static Option<Integer> width(Object value) {
        return Option.of(value)
                .filter(number -> number instanceof Integer || number instanceof Long)
                .map(number -> ((Number) number).longValue())
                .filter(width -> width >= MIN_WIDTH && width <= MAX_WIDTH)
                .map(Long::intValue);
    }

    /**
     * The heights of the panes of a left column {@code value} is, if it is an array of {@code count} whole numbers of
     * percent that leave each pane, the last one too, at least {@link #MIN_HEIGHT}.
     */
    static Option<List<Integer>> heights(Object value, int count) {
        return Option.of(value)
                .filter(java.util.List.class::isInstance)
                .map(array -> List.ofAll((java.util.List<?>) array))
                .filter(numbers -> numbers.size() == count
                        && numbers.forAll(number -> number instanceof Integer || number instanceof Long))
                .map(numbers -> numbers.map(number -> ((Number) number).longValue()))
                .filter(heights -> heights.forAll(height -> height >= MIN_HEIGHT)
                        && 100 - heights.sum().longValue() >= MIN_HEIGHT)
                .map(heights -> heights.map(Long::intValue));
    }

    /**
     * The ids {@code value} holds, if it is an array of text: each once, in its order.
     */
    private static Option<List<String>> ids(Object value) {
        return Option.of(value)
                .filter(java.util.List.class::isInstance)
                .map(array -> List.ofAll((java.util.List<?>) array))
                .filter(items -> items.forAll(String.class::isInstance))
                .map(items -> items.map(String.class::cast).distinct());
    }

    /**
     * The gh executable to run: the one chosen, else the one on the PATH.
     */
    public String gh() {
        return ghExecutable().getOrElse("gh");
    }

    /**
     * The settings of {@code keys} as they are kept in the settings file, by the path of their key, each name of a
     * table it is in and then its own; a setting at its initial value has none, unless it is always kept, nor has
     * one only the user writes.
     */
    Map<List<String>, Object> entries(SettingKeys keys) {
        return keys.all().foldLeft(LinkedHashMap.empty(), (entries, key) -> entries.merge(key.entriesOf(this)));
    }

    /**
     * Settings as read, with the keys that had an invalid value.
     */
    record Read(Settings settings, List<String> invalid) {}

    /**
     * A setting that is held, with its value.
     */
    public record Held<T>(SettingKey<T> key, T value) {}
}
