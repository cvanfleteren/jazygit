package net.vanfleteren.jazygit.kernel.settings;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.util.function.Function;
import lombok.With;

/**
 * What the user chose to keep from one session to the next.
 *
 * @param theme             the theme the UI is drawn in
 * @param ghExecutable      the gh executable to run, if not the one on the PATH
 * @param repositoryWidth   the share of the width the left column takes on the repository screen, in percent
 * @param pullRequestsWidth the share of the width the left column takes on the pull requests screen, in percent
 * @param diffStyle         how the diffs of the working tree, of a commit and of a stash are drawn
 * @param diffSyntax        whether the rich view colours the code of a diff by its syntax (ADR 53)
 * @param diffWords         whether the rich view highlights the words that changed in a changed line, not only the
 *                          line
 * @param repositoryHeights   the share of the height of the left column of the repository screen each pane under the
 *                            status takes but the last, which takes what is left, in percent
 * @param pullRequestsHeights the share of the height of the left column of the pull requests screen each pane under
 *                            the status takes but the last, which takes what is left, in percent
 * @param animations          whether what moves on the screen, like the left column stepped wider or narrower,
 *                            moves there smoothly, rather than at once (ADR 81)
 * @param autoRefresh         whether the repository is read again every so often, to show what changed outside the
 *                            app (ADR 125)
 * @param autoFetch           whether the remotes are fetched from at the start and every so often (ADR 125)
 * @param browsers            the browser set for each GitHub account (ADR 88)
 * @param editors             the editors a worktree can be opened in (ADR 110); only read, never written, as only
 *                            the user sets them
 * @param diffFilters         the ids of the diff filters enabled, in the order they were enabled (ADR 144)
 * @param filterDefinitions   the diff filters the user set (ADR 144); only read, never written, as only the user
 *                            sets them
 * @param agents              the agents a failed build can be handed to (ADR 161); only read, never written, as only
 *                            the user sets them
 */
@With
public record Settings(
        ThemeName theme,
        Option<String> ghExecutable,
        int repositoryWidth,
        int pullRequestsWidth,
        DiffStyle diffStyle,
        boolean diffSyntax,
        boolean diffWords,
        List<Integer> repositoryHeights,
        List<Integer> pullRequestsHeights,
        boolean animations,
        boolean autoRefresh,
        boolean autoFetch,
        AccountBrowsers browsers,
        Editors editors,
        List<String> diffFilters,
        FilterDefinitions filterDefinitions,
        Agents agents) {

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
     * The theme the UI is drawn in. The file shows it at its initial value too.
     */
    static final SettingKey<ThemeName> THEME = SettingKey.of(
                    "ui.theme", ThemeName.DARK, Codec.text(ThemeName::of, ThemeName::id))
            .withKept(Kept.ALWAYS);
    /**
     * The gh executable to run, if not the one on the PATH; one that is blank is not valid.
     */
    static final SettingKey<Option<String>> GH_EXECUTABLE = SettingKey.of(
            "github.executable",
            Option.none(),
            Codec.text(path -> Option.when(!path.isBlank(), path), path -> path).optional());
    /**
     * The width of the left column of the repository screen.
     */
    static final SettingKey<Integer> REPOSITORY_WIDTH =
            SettingKey.of("ui.repository-width", DEFAULT_WIDTH, Codec.whole(Settings::width, width -> width));
    /**
     * The width of the left column of the pull requests screen.
     */
    static final SettingKey<Integer> PULL_REQUESTS_WIDTH =
            SettingKey.of("ui.pull-requests-width", DEFAULT_WIDTH, Codec.whole(Settings::width, width -> width));
    /**
     * The diff style; the default has become rich diffs.
     */
    static final SettingKey<DiffStyle> DIFF_STYLE =
            SettingKey.of("ui.diff-style", DiffStyle.RICH, Codec.text(DiffStyle::of, DiffStyle::id));
    /**
     * Whether the rich view colours code by its syntax.
     */
    static final SettingKey<Boolean> DIFF_SYNTAX = SettingKey.of("ui.diff-syntax", true, Codec.bool());
    /**
     * Whether the rich view highlights the changed words of a line.
     */
    static final SettingKey<Boolean> DIFF_WORDS = SettingKey.of("ui.diff-words", true, Codec.bool());
    /**
     * The heights of the panes of the left column of the repository screen. The TOML writer writes a java.util list
     * as an array.
     */
    static final SettingKey<List<Integer>> REPOSITORY_HEIGHTS = SettingKey.of(
            "ui.repository-heights",
            DEFAULT_REPOSITORY_HEIGHTS,
            Codec.whole(value -> heights(value, DEFAULT_REPOSITORY_HEIGHTS.size()), List::toJavaList));
    /**
     * The heights of the panes of the left column of the pull requests screen.
     */
    static final SettingKey<List<Integer>> PULL_REQUESTS_HEIGHTS = SettingKey.of(
            "ui.pull-requests-heights",
            DEFAULT_PULL_REQUESTS_HEIGHTS,
            Codec.whole(value -> heights(value, DEFAULT_PULL_REQUESTS_HEIGHTS.size()), List::toJavaList));
    /**
     * Whether what moves on the screen moves there smoothly (ADR 81).
     */
    static final SettingKey<Boolean> ANIMATIONS = SettingKey.of("ui.animations", true, Codec.bool());
    /**
     * Whether the repository is read again every so often (ADR 125).
     */
    static final SettingKey<Boolean> AUTO_REFRESH = SettingKey.of("git.auto-refresh", true, Codec.bool());
    /**
     * Whether the remotes are fetched from every so often (ADR 125).
     */
    static final SettingKey<Boolean> AUTO_FETCH = SettingKey.of("git.auto-fetch", true, Codec.bool());
    /**
     * The browser of each GitHub account (ADR 88): a table of tables, by host and then by login.
     */
    static final SettingKey<AccountBrowsers> BROWSERS = SettingKey.of(
            "github.browsers",
            AccountBrowsers.NONE,
            Codec.of(
                    Codec.whole(AccountBrowsers::read, browsers -> browsers)::decode,
                    (key, browsers) -> browsers.entries()));
    /**
     * The editors (ADR 110). An entry that is wrong is left out, and reported; the others still apply. Only editors
     * that are no array at all are not valid as a whole.
     */
    static final SettingKey<Editors> EDITORS = SettingKey.userOwned(Editors.KEY, Editors.NONE, Codec.readOnly(value -> {
        Editors.Read read = Editors.read(value);
        return new Decoded<>(Option.some(read.editors()), read.invalid());
    }));
    /**
     * The ids of the diff filters enabled (ADR 144), each once, in the order they were enabled.
     */
    static final SettingKey<List<String>> DIFF_FILTERS =
            SettingKey.of("ui.diff-filters", List.empty(), Codec.whole(Settings::ids, List::toJavaList));
    /**
     * The diff filters the user set (ADR 144); as the editors.
     */
    static final SettingKey<FilterDefinitions> FILTER_DEFINITIONS =
            SettingKey.userOwned(FilterDefinitions.KEY, FilterDefinitions.NONE, Codec.readOnly(value -> {
                FilterDefinitions.Read read = FilterDefinitions.read(value, false);
                return new Decoded<>(Option.some(read.filters()), read.invalid());
            }));
    /**
     * The agents a failed build can be handed to (ADR 161); as the editors.
     */
    static final SettingKey<Agents> AGENTS = SettingKey.userOwned(Agents.KEY, Agents.NONE, Codec.readOnly(value -> {
        Agents.Read read = Agents.read(value);
        return new Decoded<>(Option.some(read.agents()), read.invalid());
    }));

    /**
     * The settings of a first session: each at its initial value.
     */
    public static final Settings DEFAULT = new Settings(
            THEME.initial(),
            GH_EXECUTABLE.initial(),
            REPOSITORY_WIDTH.initial(),
            PULL_REQUESTS_WIDTH.initial(),
            DIFF_STYLE.initial(),
            DIFF_SYNTAX.initial(),
            DIFF_WORDS.initial(),
            REPOSITORY_HEIGHTS.initial(),
            PULL_REQUESTS_HEIGHTS.initial(),
            ANIMATIONS.initial(),
            AUTO_REFRESH.initial(),
            AUTO_FETCH.initial(),
            BROWSERS.initial(),
            EDITORS.initial(),
            DIFF_FILTERS.initial(),
            FILTER_DEFINITIONS.initial(),
            AGENTS.initial());

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
     * The settings that {@code value} finds by their dotted key: the default for the ones it does not find, and
     * for the ones it finds no valid value for, whose keys come with them.
     */
    static Read read(Function<String, Option<Object>> value) {
        return Setting.ALL.foldLeft(new Read(DEFAULT, List.empty()), (read, setting) -> setting.readInto(read, value));
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
        return ghExecutable.getOrElse("gh");
    }

    /**
     * The settings as they are kept in the settings file, by the path of their key, each name of a table it is in
     * and then its own; a setting left at its default has none. The editors are not among them: the user writes
     * them, and saving leaves them as they are.
     */
    Map<List<String>, Object> entries() {
        return Setting.ALL.foldLeft(
                LinkedHashMap.empty(), (entries, setting) -> entries.merge(setting.entriesOf(this)));
    }

    /**
     * Settings as read, with the keys that had an invalid value.
     */
    record Read(Settings settings, List<String> invalid) {}
}
