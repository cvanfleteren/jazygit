package net.vanfleteren.jazygit.feature.layout;

import io.vavr.collection.List;
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.LeftPanes;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Preferences.Dragged;
import net.vanfleteren.jazygit.state.Preferences.DraggedHeights;
import net.vanfleteren.jazygit.state.Screen;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * How wide the left column of each screen is, and how tall its panes are, which is kept for the next session. A width
 * or a height that is dragged shows while the user drags, and is kept when they let go. A pane, or the content panel,
 * can be maximized for the session (ADR 92).
 */
public final class LayoutUpdate {

    /**
     * How many percent of the width a step makes the left column wider or narrower, and of the height a pane taller
     * or shorter.
     */
    public static final int STEP = 5;

    // The panes of the left column of each screen under the status, top to bottom, by the id of their element.
    private static final List<String> REPOSITORY_PANES =
            List.of(Scopes.FILES.id(), Scopes.BRANCHES.id(), Scopes.COMMITS.id(), Scopes.STASH.id());
    private static final List<String> PULL_REQUESTS_PANES =
            List.of(Scopes.PR_REPOSITORY.id(), Scopes.PR_REVIEW_REQUESTED.id(), Scopes.PR_AUTHORED.id());

    private LayoutUpdate() {}

    public static Next update(Model model, LayoutMsg msg) {
        return switch (msg) {
            // The keys and the borders work on the panes, not behind a popup.
            case LayoutMsg.Stepped stepped when model.popup().isDefined() -> Next.of(model);
            case LayoutMsg.Resized resized when model.popup().isDefined() -> Next.of(model);
            case LayoutMsg.PaneResized resized when model.popup().isDefined() -> Next.of(model);
            case LayoutMsg.PaneStepped stepped when model.popup().isDefined() -> Next.of(model);
            case LayoutMsg.MaximizeToggled toggled when model.popup().isDefined() -> Next.of(model);
            // A step ends a drag, of which a release that the terminal did not report would keep the width dragged to,
            // and the content panel maximized, from the width it is shown with.
            case LayoutMsg.Stepped(int by) ->
                keep(
                        model.mapPreferences(preferences ->
                                preferences.withDragged(Option.none()).withContentMaximized(model.screen(), false)),
                        model.screen(),
                        model.preferences().leftWidth(model.screen()) + by);
            case LayoutMsg.Resized(Screen screen, int width) ->
                Next.of(model.mapPreferences(preferences -> preferences
                        .withContentMaximized(screen, false)
                        .withDragged(Option.of(new Dragged(screen, Settings.clampWidth(width))))));
            case LayoutMsg.Settled(Screen screen) ->
                model.preferences()
                        .dragged()
                        .filter(drag -> drag.screen() == screen)
                        .map(drag -> keep(
                                model.mapPreferences(preferences -> preferences.withDragged(Option.none())),
                                screen,
                                drag.width()))
                        .getOrElse(() -> Next.of(model));
            // A drag ends a maximized pane, from the heights the panes are shown with.
            case LayoutMsg.PaneResized(Screen screen, int pane, int bottom) ->
                Next.of(model.mapPreferences(preferences -> preferences
                        .withMaximizedPane(screen, Option.none())
                        .withDraggedHeights(Option.of(new DraggedHeights(
                                screen, resized(LeftPanes.shownHeights(model, screen), pane, bottom))))));
            case LayoutMsg.PaneSettled(Screen screen) ->
                model.preferences()
                        .draggedHeights()
                        .filter(drag -> drag.screen() == screen)
                        .map(drag -> keep(
                                model.mapPreferences(preferences -> preferences.withDraggedHeights(Option.none())),
                                model.preferences().keepingHeights(screen, drag.heights())))
                        .getOrElse(() -> Next.of(model));
            case LayoutMsg.PaneStepped(int by) ->
                focusedPane(model).map(pane -> stepPane(model, pane, by)).getOrElse(() -> Next.of(model));
            case LayoutMsg.MaximizeToggled() -> Next.of(toggleMaximized(model));
            case LayoutMsg.Measured(int rows) ->
                Next.of(model.mapPreferences(preferences -> preferences.withColumnRows(Option.of(rows))));
        };
    }

    /**
     * {@code heights} with the bottom border of pane {@code pane} at {@code bottom} percent below the top of the first.
     */
    private static List<Integer> resized(List<Integer> heights, int pane, int bottom) {
        return Settings.resizedHeights(
                heights, pane, bottom - heights.take(pane).sum().intValue());
    }

    /**
     * The pane of the left column of the screen shown that had the focus last, counted from the first under the
     * status; none when the content panel had it.
     */
    private static Option<Integer> focusedPane(Model model) {
        List<String> panes = model.screen() == Screen.REPOSITORY ? REPOSITORY_PANES : PULL_REQUESTS_PANES;
        return model.shownPane().map(panes::indexOf).filter(pane -> pane >= 0);
    }

    /**
     * Makes pane {@code pane} of the left column of the screen shown {@code by} percent taller, or more if that does
     * not show it a row taller, or shorter when it is negative, and keeps the heights. It ends a drag of a border and
     * a maximized pane, from the heights the panes are shown with. Nothing changes when no step changes the rows of
     * the pane, as the panes around it are as short as they get, or the stash shows all its stashes.
     */
    private static Next stepPane(Model model, int pane, int by) {
        Screen screen = model.screen();
        List<Integer> shown = LeftPanes.shownHeights(model, screen);
        boolean maximized = model.preferences().maximizedPane(screen).isDefined();
        Model ended = model.mapPreferences(
                preferences -> preferences.withDraggedHeights(Option.none()).withMaximizedPane(screen, Option.none()));
        return stepped(ended, screen, stashShown(ended, screen, shown, pane), pane, by)
                .orElse(Option.when(maximized, shown))
                .map(heights -> keep(ended, ended.preferences().keepingHeights(screen, heights)))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * {@code heights} with pane {@code pane} {@code by} percent taller, or more until it shows a row taller in the
     * column the last frame drew, or shorter when it is negative; none when no step changes its rows. A step is
     * {@code by} itself when the rows of the column are not known yet.
     */
    private static Option<List<Integer>> stepped(Model model, Screen screen, List<Integer> heights, int pane, int by) {
        return model.preferences()
                .columnRows()
                .fold(() -> Option.of(moved(heights, pane, by)).filter(moved -> !moved.equals(heights)), column -> {
                    int rows = LeftPanes.sized(model, screen, heights, column).get(pane);
                    return Stream.rangeClosed(Math.abs(by), 100)
                            .map(step -> moved(heights, pane, Integer.signum(by) * step))
                            .find(moved -> LeftPanes.sized(model, screen, moved, column)
                                            .get(pane)
                                    != rows);
                });
    }

    /**
     * {@code heights} with pane {@code pane} {@code by} percent taller: its bottom border moved, as a drag of it does,
     * or for the last pane, which has none of its own, the border above it. No pane gets shorter than
     * {@link Settings#MIN_HEIGHT}.
     */
    private static List<Integer> moved(List<Integer> heights, int pane, int by) {
        int last = heights.size();
        return pane < last
                ? Settings.resizedHeights(heights, pane, heights.get(pane) + by)
                : Settings.resizedHeights(heights, last - 1, heights.get(last - 1) - by);
    }

    /**
     * {@code heights} with the share of the stash of the repository screen the rows it is shown with, but no less
     * than {@link Settings#MIN_HEIGHT}, and the commits above it taking the rest, when pane {@code pane} is one of
     * the two: a stash that shows all its stashes is not made taller, and the commits made shorter give it rows it
     * shows. Unchanged when the rows of the column are not known yet.
     */
    private static List<Integer> stashShown(Model model, Screen screen, List<Integer> heights, int pane) {
        int commits = heights.size() - 1;
        return model.preferences()
                .columnRows()
                .filter(column -> screen == Screen.REPOSITORY && pane >= commits)
                .map(column -> {
                    int stash = LeftPanes.sized(model, screen, heights, column).last();
                    int above = heights.take(commits).sum().intValue();
                    int bottom = Math.round((column - LeftPanes.STATUS_ROWS - stash) * 100f / column);
                    return heights.update(
                            commits,
                            Math.max(heights.get(commits), Math.min(bottom, 100 - Settings.MIN_HEIGHT) - above));
                })
                .getOrElse(heights);
    }

    /**
     * Maximizes the pane of the left column, or the content panel, of the screen shown that had the focus last, or no
     * longer does if it is. Maximizing a pane of the left column ends another one being maximized; the content panel
     * is maximized apart from them.
     */
    private static Model toggleMaximized(Model model) {
        Screen screen = model.screen();
        return focusedPane(model)
                .map(pane -> model.mapPreferences(preferences -> preferences.withMaximizedPane(
                        screen, preferences.maximizedPane(screen).contains(pane) ? Option.none() : Option.of(pane))))
                .orElse(() -> model.shownPane()
                        .map(content -> model.mapPreferences(preferences ->
                                preferences.withContentMaximized(screen, !preferences.contentMaximized(screen)))))
                .getOrElse(model);
    }

    private static Next keep(Model model, Screen screen, int width) {
        return keep(model, model.preferences().keepingWidth(screen, Settings.clampWidth(width)));
    }

    /**
     * Keeps the {@code chosen} settings, saving them if they changed.
     */
    private static Next keep(Model model, Settings chosen) {
        Settings kept = model.preferences().settings();
        Model shown = model.mapPreferences(preferences -> preferences.withSettings(chosen));
        return chosen.equals(kept) ? Next.of(shown) : new Next(shown, List.of(new Cmd.SaveSettings(kept, chosen)));
    }
}
