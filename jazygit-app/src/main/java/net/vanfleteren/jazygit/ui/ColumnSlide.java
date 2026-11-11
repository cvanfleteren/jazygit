package net.vanfleteren.jazygit.ui;

import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.state.Screen;

/**
 * How many columns the left column of {@code screen} takes on a screen {@code width} columns wide, on its way from
 * {@code from} columns to {@code to} since {@code start}, as a {@link Slide} gets there (ADR 81). At rest, it is as
 * many columns from and to.
 *
 * @param screen the screen whose left column it is
 * @param width  how many columns wide the screen is
 * @param from   how many columns the column took when it started on its way
 * @param to     how many columns it is on its way to
 * @param start  when it started on its way
 */
public record ColumnSlide(Screen screen, int width, int from, int to, Instant start) {

    /**
     * How long the column takes to get where it is going.
     */
    public static final Duration LENGTH = Slide.LENGTH;

    /**
     * The left column of {@code screen} in a frame drawn at {@code now} on a screen {@code width} columns wide, after
     * it was {@code before} in the frame before, if one was drawn: on its way to {@code columns}, which it slides to
     * while the {@code animations} are on and it is not {@code dragged}, else there at once. In the first frame, it is
     * there at rest.
     */
    public static ColumnSlide next(
            Option<ColumnSlide> before,
            Screen screen,
            int width,
            int columns,
            boolean animations,
            boolean dragged,
            Instant now) {
        return before.map(shown -> shown.toward(screen, width, columns, animations && !dragged, now))
                .getOrElse(() -> at(screen, width, columns));
    }

    /**
     * The left column of {@code screen}, on a screen {@code width} columns wide, at rest {@code columns} wide.
     */
    public static ColumnSlide at(Screen screen, int width, int columns) {
        return new ColumnSlide(screen, width, columns, columns, Instant.EPOCH);
    }

    /**
     * The column on its way to {@code columns} from where it is at {@code now}, if it is to {@code slide} there: at
     * once, as at rest, unless it does, and when it goes to another screen or the screen changes width, which it was
     * not chosen to take. On its way there already, it keeps on its way.
     */
    public ColumnSlide toward(Screen screen, int width, int columns, boolean slide, Instant now) {
        if (!slide || screen != this.screen || width != this.width) {
            return at(screen, width, columns);
        }
        return columns == to ? this : new ColumnSlide(screen, width, columnsAt(now), columns, now);
    }

    /**
     * How many columns the column takes at {@code now}.
     */
    public int columnsAt(Instant now) {
        return Slide.at(from, to, start, now);
    }

    /**
     * Whether the column is still on its way at {@code now}, so a frame after it moves it.
     */
    public boolean moving(Instant now) {
        return columnsAt(now) != to;
    }
}
