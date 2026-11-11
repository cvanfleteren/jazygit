package net.vanfleteren.jazygit.state.preferences;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.git.model.DiffContext;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.settings.DiffLook;
import net.vanfleteren.jazygit.settings.DiffStyle;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.settings.SettingsProblem;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.settings.ThemeName;

/**
 * How the user wants things shown: what is kept from one session to the next, and what holds for this one only.
 *
 * @param settings      what the user chose to keep from one session to the next, like the theme
 * @param themeOverride the theme asked for at startup, or highlighted in the theme dialog, for this session only,
 *                      until the user chooses one
 * @param repositoryTheme the theme the git configuration gives the repository shown, which the saved one gives way
 *                        to (ADR 70)
 * @param ignoreWhitespace whether the diffs leave out changes to whitespace only, for this session only
 * @param context       how many lines the diffs show around a change, for this session only (ADR 76)
 * @param dragged       the width the left column of a screen is dragged to, which is kept once it is let go
 * @param draggedHeights the heights the panes of the left column of a screen are dragged to, which are kept once
 *                       they are let go
 * @param columnRows    how many rows the left column has, as the last frame drew it, if one was (ADR 92)
 * @param maximizedPanes the pane of the left column of each screen that is maximized, counted from the first under
 *                       the status, for this session only (ADR 92)
 * @param collapsedColumns the screens whose left column is collapsed, which maximizes their content panel, for this
 *                         session only (ADR 92, ADR 104)
 * @param file          what the settings file had when it was last read or written, to tell what changed in it since
 *                      (ADR 111)
 * @param reported      what was wrong in the settings file as last reported, at the start or since (ADR 111)
 */
@With
public record Preferences(
        Settings settings,
        Option<ThemeName> themeOverride,
        RepositoryTheme repositoryTheme,
        boolean ignoreWhitespace,
        DiffContext context,
        Option<Dragged> dragged,
        Option<DraggedHeights> draggedHeights,
        Option<Integer> columnRows,
        Map<Screen, Integer> maximizedPanes,
        Set<Screen> collapsedColumns,
        SettingsStore.Loaded file,
        Option<SettingsProblem> reported) {

    public static final Preferences DEFAULT = new Preferences(
            Settings.DEFAULT,
            Option.none(),
            RepositoryTheme.NONE,
            false,
            DiffContext.DEFAULT,
            Option.none(),
            Option.none(),
            Option.none(),
            HashMap.empty(),
            HashSet.empty(),
            SettingsStore.Loaded.DEFAULT,
            Option.none());

    /**
     * How the diffs of the files and the commits are read: a merge against its first parent when changes are drawn
     * rich, as the rich view does not draw git's combined diff (ADR 44).
     */
    public DiffOptions diffOptions() {
        return new DiffOptions(ignoreWhitespace, settings.diffStyle() == DiffStyle.RICH, context);
    }

    /**
     * How the diffs of the working tree, of a commit and of a stash are drawn, whether the rich view colours their
     * code by its syntax (ADR 53), and whether it highlights the words that changed in a line.
     */
    public DiffLook diffLook() {
        return new DiffLook(settings.diffStyle(), settings.diffSyntax(), settings.diffWords());
    }

    /**
     * The theme the UI is drawn in: the one asked for at startup, else the one of the repository, else the saved one.
     */
    public ThemeName theme() {
        return themeOverride.orElse(repositoryTheme.theme()).getOrElse(settings.theme());
    }

    /**
     * How wide the left column of {@code screen} is when it is not collapsed, in percent of the width: where it is
     * dragged to, else the width kept.
     */
    public int leftWidth(Screen screen) {
        return dragged.filter(drag -> drag.screen() == screen)
                .map(Dragged::width)
                .getOrElse(() -> keptWidth(screen));
    }

    /**
     * The pane of the left column of {@code screen} that is maximized, counted from the first under the status.
     */
    public Option<Integer> maximizedPane(Screen screen) {
        return maximizedPanes.get(screen);
    }

    /**
     * These preferences with pane {@code pane} of the left column of {@code screen} maximized, or none.
     */
    public Preferences withMaximizedPane(Screen screen, Option<Integer> pane) {
        return withMaximizedPanes(pane.fold(() -> maximizedPanes.remove(screen), at -> maximizedPanes.put(screen, at)));
    }

    /**
     * Whether the left column of {@code screen} is collapsed, which leaves all but a sliver of the width to the
     * content panel (ADR 104).
     */
    public boolean leftCollapsed(Screen screen) {
        return collapsedColumns.contains(screen);
    }

    /**
     * These preferences with the left column of {@code screen} collapsed, or not.
     */
    public Preferences withLeftCollapsed(Screen screen, boolean collapsed) {
        return withCollapsedColumns(collapsed ? collapsedColumns.add(screen) : collapsedColumns.remove(screen));
    }

    /**
     * How wide the left column of {@code screen} is kept, in percent of the width.
     */
    public int keptWidth(Screen screen) {
        return switch (screen) {
            case REPOSITORY -> settings.repositoryWidth();
            case PULL_REQUESTS -> settings.pullRequestsWidth();
        };
    }

    /**
     * The settings with the left column of {@code screen} kept {@code width} wide.
     */
    public Settings keepingWidth(Screen screen, int width) {
        return switch (screen) {
            case REPOSITORY -> settings.withRepositoryWidth(width);
            case PULL_REQUESTS -> settings.withPullRequestsWidth(width);
        };
    }

    /**
     * How tall the panes of the left column of {@code screen} under the status are but the last, in percent of its
     * height: where they are dragged to, else the heights kept.
     */
    public List<Integer> leftHeights(Screen screen) {
        return draggedHeights
                .filter(drag -> drag.screen() == screen)
                .map(DraggedHeights::heights)
                .getOrElse(() -> keptHeights(screen));
    }

    /**
     * How tall the panes of the left column of {@code screen} under the status are kept but the last, in percent of
     * its height.
     */
    public List<Integer> keptHeights(Screen screen) {
        return switch (screen) {
            case REPOSITORY -> settings.repositoryHeights();
            case PULL_REQUESTS -> settings.pullRequestsHeights();
        };
    }

    /**
     * The settings with the panes of the left column of {@code screen} kept {@code heights} tall.
     */
    public Settings keepingHeights(Screen screen, List<Integer> heights) {
        return switch (screen) {
            case REPOSITORY -> settings.withRepositoryHeights(heights);
            case PULL_REQUESTS -> settings.withPullRequestsHeights(heights);
        };
    }

    /**
     * The left column of {@code screen}, dragged to {@code width} percent of the width.
     */
    public record Dragged(Screen screen, int width) {}

    /**
     * The panes of the left column of {@code screen}, dragged to {@code heights} percent of its height.
     */
    public record DraggedHeights(Screen screen, List<Integer> heights) {}
}
