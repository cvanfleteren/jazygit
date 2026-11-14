package net.vanfleteren.jazygit.ui;

import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.state.preferences.Screen;

/**
 * How many rows the command log of {@code screen} takes, on its way from {@code from} rows to {@code to} since
 * {@code start}, as a {@link Slide} gets there: it shrinks as the left column collapses and grows back as it opens,
 * sliding along with it (ADR 81, 104). At rest, it is as many rows from and to.
 *
 * @param screen the screen whose command log it is
 * @param from   how many rows the log took when it started on its way
 * @param to     how many rows it is on its way to
 * @param start  when it started on its way
 */
public record CommandLogSlide(Screen screen, int from, int to, Instant start) {

    /**
     * The command log of {@code screen} in a frame drawn at {@code now}, after it was {@code before} in the frame
     * before, if one was drawn: on its way to {@code rows}, which it slides to while the {@code animations} are on,
     * else there at once. In the first frame, it is there at rest.
     */
    public static CommandLogSlide next(
            Option<CommandLogSlide> before, Screen screen, int rows, boolean animations, Instant now) {
        return before.map(shown -> shown.toward(screen, rows, animations, now)).getOrElse(() -> at(screen, rows));
    }

    /**
     * The command log of {@code screen} at rest {@code rows} tall.
     */
    public static CommandLogSlide at(Screen screen, int rows) {
        return new CommandLogSlide(screen, rows, rows, Instant.EPOCH);
    }

    /**
     * The log on its way to {@code rows} from where it is at {@code now}, if it is to {@code slide} there: at once,
     * as at rest, unless it does, and when it goes to another screen, as the left column does. On its way there
     * already, it keeps on its way.
     */
    public CommandLogSlide toward(Screen screen, int rows, boolean slide, Instant now) {
        if (!slide || screen != this.screen) {
            return at(screen, rows);
        }
        return rows == to ? this : new CommandLogSlide(screen, rowsAt(now), rows, now);
    }

    /**
     * How many rows the log takes at {@code now}.
     */
    public int rowsAt(Instant now) {
        return Slide.at(from, to, start, now);
    }

    /**
     * Whether the log is still on its way at {@code now}, so a frame after it moves it.
     */
    public boolean moving(Instant now) {
        return rowsAt(now) != to;
    }
}
