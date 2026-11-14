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
        Editors editors) {

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
            // The default has become rich diffs.
            DiffStyle.RICH,
            true,
            true,
            DEFAULT_REPOSITORY_HEIGHTS,
            DEFAULT_PULL_REQUESTS_HEIGHTS,
            true,
            true,
            true,
            AccountBrowsers.NONE,
            Editors.NONE);

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
     * The key of whether the rich view highlights the changed words of a line in the settings file.
     */
    static final String DIFF_WORDS = "ui.diff-words";
    /**
     * The key of the heights of the panes of the left column of the repository screen in the settings file.
     */
    static final String REPOSITORY_HEIGHTS = "ui.repository-heights";
    /**
     * The key of the heights of the panes of the left column of the pull requests screen in the settings file.
     */
    static final String PULL_REQUESTS_HEIGHTS = "ui.pull-requests-heights";
    /**
     * The key of whether what moves on the screen moves there smoothly in the settings file (ADR 81).
     */
    static final String ANIMATIONS = "ui.animations";
    /**
     * The key of whether the repository is read again every so often in the settings file (ADR 125).
     */
    static final String AUTO_REFRESH = "git.auto-refresh";
    /**
     * The key of whether the remotes are fetched from every so often in the settings file (ADR 125).
     */
    static final String AUTO_FETCH = "git.auto-fetch";
    /**
     * The key of the table of the browser of each GitHub account in the settings file (ADR 88).
     */
    static final String BROWSERS = "github.browsers";

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
