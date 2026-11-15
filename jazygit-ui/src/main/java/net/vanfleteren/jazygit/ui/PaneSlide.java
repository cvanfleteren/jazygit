package net.vanfleteren.jazygit.ui;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.state.preferences.Screen;

/**
 * How many rows the panes of the left column of {@code screen}, under the status, take in a column {@code column} rows
 * tall, on their way from {@code from} rows to {@code to} since {@code start}, as a {@link Slide} gets there
 * (ADR 92). All the borders between them move at once, so the panes always fill the rows they had. At rest, they are
 * as many rows from and to.
 *
 * @param screen the screen whose left column it is
 * @param column how many rows tall the column is
 * @param chosen the heights the user chose that the panes are on their way to
 * @param from   how many rows each pane took when they started on their way
 * @param to     how many rows each pane is on its way to
 * @param start  when they started on their way
 */
public record PaneSlide(Screen screen, int column, Chosen chosen, List<Integer> from, List<Integer> to, Instant start) {

    /**
     * The panes of the left column of {@code screen} in a frame drawn at {@code now} in a column {@code column} rows
     * tall, after they were {@code before} in the frame before, if one was drawn: on their way to {@code rows}, which
     * they slide to while the {@code animations} are on, they are not {@code dragged}, and the user {@code chosen}
     * them, else there at once. In the first frame, they are there at rest.
     */
    public static PaneSlide next(
            Option<PaneSlide> before,
            Screen screen,
            int column,
            Chosen chosen,
            List<Integer> rows,
            boolean animations,
            boolean dragged,
            Instant now) {
        return before.map(shown -> shown.toward(screen, column, chosen, rows, animations && !dragged, now))
                .getOrElse(() -> at(screen, column, chosen, rows));
    }

    /**
     * The panes of the left column of {@code screen}, in a column {@code column} rows tall, at rest {@code rows} tall.
     */
    public static PaneSlide at(Screen screen, int column, Chosen chosen, List<Integer> rows) {
        return new PaneSlide(screen, column, chosen, rows, rows, Instant.EPOCH);
    }

    /**
     * The panes on their way to {@code rows} from where they are at {@code now}, if they are to {@code slide} there
     * and the user {@code chosen} them: at once, as at rest, unless they do, and when they go to another screen, the
     * column changes height, or their rows change though the heights chosen do not, as the stash's do with its
     * stashes. On their way there already, they keep on their way.
     */
    public PaneSlide toward(Screen screen, int column, Chosen chosen, List<Integer> rows, boolean slide, Instant now) {
        boolean same = screen == this.screen && column == this.column && rows.size() == to.size();
        if (same && rows.equals(to)) {
            return chosen.equals(this.chosen) ? this : new PaneSlide(screen, column, chosen, from, to, start);
        }
        if (!slide || !same || chosen.equals(this.chosen)) {
            return at(screen, column, chosen, rows);
        }
        return new PaneSlide(screen, column, chosen, rowsAt(now), rows, now);
    }

    /**
     * How many rows each pane takes at {@code now}: the borders between them on their way, so the rows always add up
     * to what they took.
     */
    public List<Integer> rowsAt(Instant now) {
        List<Integer> borders = from.scanLeft(0, Integer::sum)
                .zipWith(to.scanLeft(0, Integer::sum), (was, will) -> Slide.at(was, will, start, now));
        return borders.tail().zipWith(borders, (bottom, top) -> bottom - top);
    }

    /**
     * Whether the panes are still on their way at {@code now}, so a frame after it moves them.
     */
    public boolean moving(Instant now) {
        return !rowsAt(now).equals(to);
    }

    /**
     * What the user chose the panes to be: the heights of all but the last, in percent, and the one maximized, if any.
     */
    public record Chosen(List<Integer> heights, Option<Integer> maximized) {}
}
