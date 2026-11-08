package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.settings.Settings;

/**
 * How many rows the panes of the left column under the status are shown with: as tall as their heights say, the
 * stash as tall as its stashes, or one pane maximized and the others a row in a border each (ADR 92).
 */
public final class LeftPanes {

    /**
     * The rows of the status pane, on top of the column.
     */
    public static final int STATUS_ROWS = 3;
    /**
     * The rows of a pane that shows one row: a border around it.
     */
    public static final int COLLAPSED_ROWS = 3;

    private LeftPanes() {}

    /**
     * How many rows each pane under the status takes of a column {@code column} rows tall, whose panes but the last
     * are {@code heights} percent of the column tall: the last takes what they and the status leave. The borders
     * between the panes are rounded to a row, not the panes, so moving one border leaves the others where they are.
     */
    public static List<Integer> rows(List<Integer> heights, int column) {
        List<Integer> borders = heights.scanLeft(0, Integer::sum).map(border -> Math.round(column * border / 100f));
        return borders.tail()
                .zipWith(borders, (bottom, top) -> bottom - top)
                .append(Math.max(0, column - STATUS_ROWS - borders.last()));
    }

    /**
     * How many rows each pane of the left column of {@code screen} of {@code model} is shown with in a column
     * {@code column} rows tall: the maximized pane all it can, if one is and the column has the rows for it, else as
     * tall as the heights the panes have.
     */
    public static List<Integer> shown(Model model, Screen screen, int column) {
        return maximized(model, screen, column)
                .getOrElse(() -> sized(model, screen, model.preferences().leftHeights(screen), column));
    }

    /**
     * How many rows each pane of the left column of {@code screen} of {@code model} is shown with in a column
     * {@code column} rows tall when its panes but the last are {@code heights} percent tall. On the repository
     * screen, the stash is just its rows, but at most its share and at least an empty pane, and the commits take
     * what it leaves of its share.
     */
    public static List<Integer> sized(Model model, Screen screen, List<Integer> heights, int column) {
        List<Integer> rows = rows(heights, column);
        if (screen != Screen.REPOSITORY) {
            return rows;
        }
        int share = rows.last();
        int stash = Math.max(COLLAPSED_ROWS, Math.min(stashRows(model), share));
        int commits = rows.size() - 2;
        return rows.update(commits, Math.max(0, rows.get(commits) + share - stash))
                .update(rows.size() - 1, stash);
    }

    /**
     * How many rows the stash of the repository screen of {@code model} needs to show all its stashes, in a border:
     * an empty one shows a blank line.
     */
    public static int stashRows(Model model) {
        return Math.max(1, model.repo().stashes().getOrElse(List.empty()).size()) + 2;
    }

    /**
     * How many rows each pane of the left column of {@code screen} of {@code model} is shown with in a column
     * {@code column} rows tall while one is maximized: the others a row in a border, and it what they leave. None when
     * none is, or when the column is too short to show it so.
     */
    public static Option<List<Integer>> maximized(Model model, Screen screen, int column) {
        int panes = model.preferences().leftHeights(screen).size() + 1;
        int rest = column - STATUS_ROWS - COLLAPSED_ROWS * (panes - 1);
        return model.preferences()
                .maximizedPane(screen)
                .filter(pane -> pane < panes && rest >= COLLAPSED_ROWS)
                .map(pane -> List.fill(panes, COLLAPSED_ROWS).update(pane, rest));
    }

    /**
     * The heights the panes of the left column of {@code screen} of {@code model} are shown with, in percent: while
     * one is maximized, the borders where they are, each pane at least {@link Settings#MIN_HEIGHT} tall with what
     * that takes taken from the maximized one, else the heights they have. Taken when the user resizes a pane while
     * one is maximized, which ends it.
     */
    public static List<Integer> shownHeights(Model model, Screen screen) {
        return model.preferences()
                .columnRows()
                .flatMap(column -> maximized(model, screen, column)
                        .map(rows -> raisedToLeast(
                                heights(rows, column),
                                model.preferences().maximizedPane(screen).get())))
                .getOrElse(() -> model.preferences().leftHeights(screen));
    }

    /**
     * The heights, in percent of a column {@code column} rows tall, of panes that are {@code rows} tall but the last:
     * their borders at the nearest percent, which {@link #rows} rounds back to the same row.
     */
    static List<Integer> heights(List<Integer> rows, int column) {
        List<Integer> borders = rows.init().scanLeft(0, Integer::sum).map(border -> Math.round(border * 100f / column));
        return borders.tail().zipWith(borders, (bottom, top) -> bottom - top);
    }

    /**
     * {@code heights} with each pane, the last one too, at least {@link Settings#MIN_HEIGHT} tall, and what that
     * takes taken from pane {@code from}.
     */
    static List<Integer> raisedToLeast(List<Integer> heights, int from) {
        List<Integer> all = heights.append(100 - heights.sum().intValue());
        int missing = all.map(height -> Math.max(0, Settings.MIN_HEIGHT - height))
                .sum()
                .intValue();
        List<Integer> raised = all.map(height -> Math.max(Settings.MIN_HEIGHT, height));
        return raised.update(from, raised.get(from) - missing).init();
    }
}
