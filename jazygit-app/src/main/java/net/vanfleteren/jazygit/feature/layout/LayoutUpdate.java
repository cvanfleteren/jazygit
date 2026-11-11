package net.vanfleteren.jazygit.feature.layout;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.keys.Scopes;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.preferences.LeftPanes;
import net.vanfleteren.jazygit.state.preferences.Preferences.Dragged;
import net.vanfleteren.jazygit.state.preferences.Preferences.DraggedHeights;
import net.vanfleteren.jazygit.state.preferences.SaveSettings;
import net.vanfleteren.jazygit.state.preferences.Screen;

/**
 * How wide the left column of each screen is, and how tall its panes are, which is kept for the next session. A width
 * or a height that is dragged shows while the user drags, and is kept when they let go. A pane can be maximized for
 * the session (ADR 92), and the left column collapsed, which maximizes the content panel (ADR 104).
 */
public final class LayoutUpdate {

    /**
     * How many percent of the width a step makes the left column wider or narrower, and of the height a pane taller
     * or shorter.
     */
    public static final int STEP = 5;

    private LayoutUpdate() {}

    /**
     * Whether the left column dragged to {@code width} percent of the width collapses: when it is dragged past half
     * the narrowest it gets otherwise (ADR 104).
     */
    public static boolean collapses(int width) {
        return width * 2 < Settings.MIN_WIDTH;
    }

    public static Next update(Model model, LayoutMsg msg) {
        return switch (msg) {
            // The keys and the borders work on the panes, not behind a popup.
            case LayoutMsg.Stepped stepped when model.popup().isDefined() -> Next.of(model);
            case LayoutMsg.Resized resized when model.popup().isDefined() -> Next.of(model);
            case LayoutMsg.PaneResized resized when model.popup().isDefined() -> Next.of(model);
            case LayoutMsg.PaneStepped stepped when model.popup().isDefined() -> Next.of(model);
            case LayoutMsg.MaximizeToggled toggled when model.popup().isDefined() -> Next.of(model);
            case LayoutMsg.Expanded expanded when model.popup().isDefined() -> Next.of(model);
            case LayoutMsg.Stepped(int by) -> stepWidth(model, by);
            // Dragged far enough, the column collapses, until it is dragged out again.
            case LayoutMsg.Resized(Screen screen, int width) ->
                Next.of(model.mapPreferences(preferences -> collapses(width)
                        ? preferences.withLeftCollapsed(screen, true).withDragged(Option.none())
                        : preferences
                                .withLeftCollapsed(screen, false)
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
            // The panes of a collapsed column are not drawn, so they are not resized either (ADR 104).
            case LayoutMsg.PaneStepped stepped when model.preferences().leftCollapsed(model.screen()) -> Next.of(model);
            case LayoutMsg.PaneStepped(int by) ->
                focusedPane(model).map(pane -> stepPane(model, pane, by)).getOrElse(() -> Next.of(model));
            case LayoutMsg.MaximizeToggled() -> Next.of(toggleMaximized(model));
            case LayoutMsg.Expanded(Screen screen) ->
                Next.of(model.mapPreferences(preferences -> preferences.withLeftCollapsed(screen, false)));
            case LayoutMsg.Measured(int rows) ->
                Next.of(model.mapPreferences(preferences -> preferences.withColumnRows(Option.of(rows))));
        };
    }

    /**
     * Makes the left column of the screen shown {@code by} percent of the width wider, or narrower when it is negative,
     * and keeps the width. A step narrower at the narrowest collapses the column, for the session, and a step wider
     * opens a collapsed column at the narrowest, which is kept (ADR 104). A step ends a drag, of which a release that
     * the terminal did not report would keep the width dragged to.
     */
    private static Next stepWidth(Model model, int by) {
        Screen screen = model.screen();
        Model ended = model.mapPreferences(preferences -> preferences.withDragged(Option.none()));
        if (model.preferences().leftCollapsed(screen)) {
            return by > 0
                    ? keep(
                            ended.mapPreferences(preferences -> preferences.withLeftCollapsed(screen, false)),
                            screen,
                            Settings.MIN_WIDTH)
                    : Next.of(ended);
        }
        int shown = model.preferences().leftWidth(screen);
        return by < 0 && shown == Settings.MIN_WIDTH
                ? Next.of(ended.mapPreferences(preferences -> preferences.withLeftCollapsed(screen, true)))
                : keep(ended, screen, shown + by);
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
        List<String> panes = Scopes.leftPanes(model.screen());
        return model.shownPane().map(panes::indexOf).filter(pane -> pane >= 0);
    }

    /**
     * Makes pane {@code pane} of the left column of the screen shown {@code by} percent taller, or more if that does
     * not show it a row taller, or shorter when it is negative, and keeps the heights. It ends a drag of a border and
     * a maximized pane, from the heights the panes are shown with. When the other panes are as short as they get, a
     * step taller maximizes the pane instead, which a step taller then leaves as it is. Nothing changes when no step
     * changes the rows of the pane, as the panes around it are as short as they get, or the stash shows all its
     * stashes.
     */
    private static Next stepPane(Model model, int pane, int by) {
        Screen screen = model.screen();
        if (by > 0 && maximizedShown(model, screen, pane)) {
            return Next.of(model);
        }
        List<Integer> shown = LeftPanes.shownHeights(model, screen);
        boolean maximized = model.preferences().maximizedPane(screen).isDefined();
        Model ended = model.mapPreferences(
                preferences -> preferences.withDraggedHeights(Option.none()).withMaximizedPane(screen, Option.none()));
        List<Integer> heights = stashShown(ended, screen, shown, pane);
        return stepped(ended, screen, heights, pane, by)
                .map(stepped -> keep(ended, ended.preferences().keepingHeights(screen, stepped)))
                .orElse(() -> (by > 0 ? maximizing(ended, screen, heights, pane) : Option.<Model>none())
                        .map(oneMaximized ->
                                keep(oneMaximized, oneMaximized.preferences().keepingHeights(screen, heights))))
                .orElse(() -> Option.when(
                        maximized, () -> keep(ended, ended.preferences().keepingHeights(screen, shown))))
                .getOrElse(() -> Next.of(model));
    }

    /**
     * Whether pane {@code pane} of the left column of {@code screen} of {@code model} is maximized and shown so, in
     * the column the last frame drew.
     */
    private static boolean maximizedShown(Model model, Screen screen, int pane) {
        return model.preferences().maximizedPane(screen).contains(pane)
                && model.preferences()
                        .columnRows()
                        .flatMap(column -> LeftPanes.maximized(model, screen, column))
                        .isDefined();
    }

    /**
     * {@code model} with pane {@code pane} of the left column of {@code screen} maximized, when the panes are
     * {@code heights} tall and the others are as short as a step makes them: it then gets the rows of the others but
     * the 3 each keeps. None when that shows it no taller, or when it is a stash that shows all its stashes.
     */
    private static Option<Model> maximizing(Model model, Screen screen, List<Integer> heights, int pane) {
        Model maximized = model.mapPreferences(preferences -> preferences.withMaximizedPane(screen, Option.of(pane)));
        return model.preferences().columnRows().flatMap(column -> {
            List<Integer> rows = LeftPanes.sized(model, screen, heights, column);
            boolean stash = screen == Screen.REPOSITORY && pane == heights.size();
            return LeftPanes.maximized(maximized, screen, column)
                    .filter(shown -> !(stash && rows.get(pane) >= LeftPanes.stashRows(model))
                            && shown.get(pane) > rows.get(pane))
                    .map(shown -> maximized);
        });
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
     * {@code heights} with pane {@code pane} {@code by} percent taller, taken from the panes under it, the nearest
     * first, and when they are as short as they get, from the panes above it, the nearest first: it grows as far as a
     * maximized pane does. Shorter, it moves its bottom border, as a drag of it does, or for the last pane, which has
     * none of its own, the border above it. No pane gets shorter than {@link Settings#MIN_HEIGHT}.
     */
    private static List<Integer> moved(List<Integer> heights, int pane, int by) {
        int last = heights.size();
        if (by < 0) {
            return pane < last
                    ? Settings.resizedHeights(heights, pane, heights.get(pane) + by)
                    : Settings.resizedHeights(heights, last - 1, heights.get(last - 1) - by);
        }
        List<Integer> all = heights.append(100 - heights.sum().intValue());
        List<Integer> givers =
                List.rangeClosed(pane + 1, last).appendAll(List.range(0, pane).reverse());
        Tuple2<List<Integer>, Integer> given = givers.foldLeft(Tuple.of(all, by), (taking, giver) -> {
            int give = Math.clamp(taking._1.get(giver) - Settings.MIN_HEIGHT, 0, taking._2);
            return Tuple.of(taking._1.update(giver, taking._1.get(giver) - give), taking._2 - give);
        });
        return given._1.update(pane, given._1.get(pane) + by - given._2).init();
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
     * is maximized apart from them, by collapsing the left column (ADR 104). A collapsed column opens, whatever had the
     * focus last: a pane it hides can still have it, when the content shown for the pane had no area to take it.
     */
    private static Model toggleMaximized(Model model) {
        Screen screen = model.screen();
        if (model.preferences().leftCollapsed(screen)) {
            return model.mapPreferences(preferences -> preferences.withLeftCollapsed(screen, false));
        }
        return focusedPane(model)
                .map(pane -> model.mapPreferences(preferences -> preferences.withMaximizedPane(
                        screen, preferences.maximizedPane(screen).contains(pane) ? Option.none() : Option.of(pane))))
                .orElse(() -> model.shownPane()
                        .map(content ->
                                model.mapPreferences(preferences -> preferences.withLeftCollapsed(screen, true))))
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
        return chosen.equals(kept) ? Next.of(shown) : new Next(shown, List.of(new SaveSettings(kept, chosen)));
    }
}
