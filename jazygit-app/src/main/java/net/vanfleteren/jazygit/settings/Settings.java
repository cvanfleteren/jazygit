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
 */
@With
public record Settings(ThemeName theme, Option<String> ghExecutable, int repositoryWidth, int pullRequestsWidth) {

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
     * The settings of a first session.
     */
    public static final Settings DEFAULT = new Settings(ThemeName.DARK, Option.none(), DEFAULT_WIDTH, DEFAULT_WIDTH);

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
     * {@code width} brought within the widths the left column may have.
     */
    public static int clampWidth(int width) {
        return Math.clamp(width, MIN_WIDTH, MAX_WIDTH);
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
        return new Read(
                new Settings(
                        validTheme.getOrElse(DEFAULT.theme),
                        validGh,
                        validRepositoryWidth.getOrElse(DEFAULT_WIDTH),
                        validPullRequestsWidth.getOrElse(DEFAULT_WIDTH)),
                Option.when(theme.isDefined() && validTheme.isEmpty(), THEME)
                        .toList()
                        .appendAll(Option.when(gh.isDefined() && validGh.isEmpty(), GH_EXECUTABLE))
                        .appendAll(Option.when(
                                repositoryWidth.isDefined() && validRepositoryWidth.isEmpty(), REPOSITORY_WIDTH))
                        .appendAll(Option.when(
                                pullRequestsWidth.isDefined() && validPullRequestsWidth.isEmpty(),
                                PULL_REQUESTS_WIDTH)));
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
                .merge(widthEntry(PULL_REQUESTS_WIDTH, pullRequestsWidth));
    }

    private static Map<String, Object> widthEntry(String key, int width) {
        return width == DEFAULT_WIDTH ? LinkedHashMap.empty() : LinkedHashMap.of(key, width);
    }

    /**
     * Settings as read, with the keys that had an invalid value.
     */
    record Read(Settings settings, List<String> invalid) {}
}
