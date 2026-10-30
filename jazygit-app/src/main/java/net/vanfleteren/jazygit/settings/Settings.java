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
 * @param theme             the theme the UI is drawn in
 * @param ghExecutable      the gh executable to run, if not the one on the PATH
 * @param repositoryWidth   the share of the width the left column takes on the repository screen, in percent
 * @param pullRequestsWidth the share of the width the left column takes on the pull requests screen, in percent
 * @param diffStyle         how the diffs of the working tree, of a commit and of a stash are drawn
 * @param diffSyntax        whether the rich view colours the code of a diff by its syntax (ADR 53)
 * @param repositoryHeights   the share of the height of the left column of the repository screen each pane under the
 *                            status takes but the last, which takes what is left, in percent
 * @param pullRequestsHeights the share of the height of the left column of the pull requests screen each pane under
 *                            the status takes but the last, which takes what is left, in percent
 */
@With
public record Settings(
        ThemeName theme,
        Option<String> ghExecutable,
        int repositoryWidth,
        int pullRequestsWidth,
        DiffStyle diffStyle,
        boolean diffSyntax,
        List<Integer> repositoryHeights,
        List<Integer> pullRequestsHeights) {

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
     * The settings of a first session.
     */
    public static final Settings DEFAULT = new Settings(
            ThemeName.DARK,
            Option.none(),
            DEFAULT_WIDTH,
            DEFAULT_WIDTH,
            DiffStyle.PLAIN,
            true,
            DEFAULT_REPOSITORY_HEIGHTS,
            DEFAULT_PULL_REQUESTS_HEIGHTS);

    /**
     * The key of the theme in the settings file.
     */
    static final String THEME = "ui.theme";
    /**
     * The key of the gh executable in the settings file.
     */
    static final String GH_EXECUTABLE = "github.executable";
    /**
     * The key of the width of the left column of the repository screen in the settings file.
     */
    static final String REPOSITORY_WIDTH = "ui.repository-width";
    /**
     * The key of the width of the left column of the pull requests screen in the settings file.
     */
    static final String PULL_REQUESTS_WIDTH = "ui.pull-requests-width";
    /**
     * The key of the diff style in the settings file.
     */
    static final String DIFF_STYLE = "ui.diff-style";
    /**
     * The key of whether the rich view colours code by its syntax in the settings file.
     */
    static final String DIFF_SYNTAX = "ui.diff-syntax";
    /**
     * The key of the heights of the panes of the left column of the repository screen in the settings file.
     */
    static final String REPOSITORY_HEIGHTS = "ui.repository-heights";
    /**
     * The key of the heights of the panes of the left column of the pull requests screen in the settings file.
     */
    static final String PULL_REQUESTS_HEIGHTS = "ui.pull-requests-heights";

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
        Option<Object> theme = value.apply(THEME);
        Option<ThemeName> validTheme =
                theme.filter(String.class::isInstance).map(String.class::cast).flatMap(ThemeName::of);
        Option<Object> gh = value.apply(GH_EXECUTABLE);
        Option<String> validGh =
                gh.filter(String.class::isInstance).map(String.class::cast).filter(path -> !path.isBlank());
        Option<Object> repositoryWidth = value.apply(REPOSITORY_WIDTH);
        Option<Integer> validRepositoryWidth = repositoryWidth.flatMap(Settings::width);
        Option<Object> pullRequestsWidth = value.apply(PULL_REQUESTS_WIDTH);
        Option<Integer> validPullRequestsWidth = pullRequestsWidth.flatMap(Settings::width);
        Option<Object> diffStyle = value.apply(DIFF_STYLE);
        Option<DiffStyle> validDiffStyle = diffStyle
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .flatMap(DiffStyle::of);
        Option<Object> diffSyntax = value.apply(DIFF_SYNTAX);
        Option<Boolean> validDiffSyntax =
                diffSyntax.filter(Boolean.class::isInstance).map(Boolean.class::cast);
        Option<Object> repositoryHeights = value.apply(REPOSITORY_HEIGHTS);
        Option<List<Integer>> validRepositoryHeights =
                repositoryHeights.flatMap(heights -> heights(heights, DEFAULT_REPOSITORY_HEIGHTS.size()));
        Option<Object> pullRequestsHeights = value.apply(PULL_REQUESTS_HEIGHTS);
        Option<List<Integer>> validPullRequestsHeights =
                pullRequestsHeights.flatMap(heights -> heights(heights, DEFAULT_PULL_REQUESTS_HEIGHTS.size()));
        return new Read(
                new Settings(
                        validTheme.getOrElse(DEFAULT.theme),
                        validGh,
                        validRepositoryWidth.getOrElse(DEFAULT_WIDTH),
                        validPullRequestsWidth.getOrElse(DEFAULT_WIDTH),
                        validDiffStyle.getOrElse(DEFAULT.diffStyle),
                        validDiffSyntax.getOrElse(DEFAULT.diffSyntax),
                        validRepositoryHeights.getOrElse(DEFAULT_REPOSITORY_HEIGHTS),
                        validPullRequestsHeights.getOrElse(DEFAULT_PULL_REQUESTS_HEIGHTS)),
                Option.when(theme.isDefined() && validTheme.isEmpty(), THEME)
                        .toList()
                        .appendAll(Option.when(gh.isDefined() && validGh.isEmpty(), GH_EXECUTABLE))
                        .appendAll(Option.when(
                                repositoryWidth.isDefined() && validRepositoryWidth.isEmpty(), REPOSITORY_WIDTH))
                        .appendAll(Option.when(
                                pullRequestsWidth.isDefined() && validPullRequestsWidth.isEmpty(), PULL_REQUESTS_WIDTH))
                        .appendAll(Option.when(diffStyle.isDefined() && validDiffStyle.isEmpty(), DIFF_STYLE))
                        .appendAll(Option.when(diffSyntax.isDefined() && validDiffSyntax.isEmpty(), DIFF_SYNTAX))
                        .appendAll(Option.when(
                                repositoryHeights.isDefined() && validRepositoryHeights.isEmpty(), REPOSITORY_HEIGHTS))
                        .appendAll(Option.when(
                                pullRequestsHeights.isDefined() && validPullRequestsHeights.isEmpty(),
                                PULL_REQUESTS_HEIGHTS)));
    }

    /**
     * The width of the left column {@code value} is, if it is a whole number of percent the column may have.
     */
    private static Option<Integer> width(Object value) {
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
    private static Option<List<Integer>> heights(Object value, int count) {
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
                        .getOrElse(LinkedHashMap.empty()))
                .merge(widthEntry(REPOSITORY_WIDTH, repositoryWidth))
                .merge(widthEntry(PULL_REQUESTS_WIDTH, pullRequestsWidth))
                .merge(
                        diffStyle == DEFAULT.diffStyle
                                ? LinkedHashMap.empty()
                                : LinkedHashMap.of(DIFF_STYLE, diffStyle.id()))
                .merge(
                        diffSyntax == DEFAULT.diffSyntax
                                ? LinkedHashMap.empty()
                                : LinkedHashMap.of(DIFF_SYNTAX, diffSyntax))
                .merge(heightsEntry(REPOSITORY_HEIGHTS, repositoryHeights, DEFAULT_REPOSITORY_HEIGHTS))
                .merge(heightsEntry(PULL_REQUESTS_HEIGHTS, pullRequestsHeights, DEFAULT_PULL_REQUESTS_HEIGHTS));
    }

    private static Map<String, Object> widthEntry(String key, int width) {
        return width == DEFAULT_WIDTH ? LinkedHashMap.empty() : LinkedHashMap.of(key, width);
    }

    // The TOML writer writes a java.util list as an array.
    private static Map<String, Object> heightsEntry(String key, List<Integer> heights, List<Integer> defaults) {
        return heights.equals(defaults) ? LinkedHashMap.empty() : LinkedHashMap.of(key, heights.toJavaList());
    }

    /**
     * Settings as read, with the keys that had an invalid value.
     */
    record Read(Settings settings, List<String> invalid) {}
}
